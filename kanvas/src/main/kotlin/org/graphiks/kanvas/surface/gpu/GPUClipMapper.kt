package org.graphiks.kanvas.surface.gpu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathCommand
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb as GpuPathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapAxisAligned
import org.graphiks.math.matrix.mapAxisAlignedRect

/**
 * Maps captured clips into the legacy coverage transport.
 *
 * Typed transform snapshots are consumed here, before that transport can see
 * them. Only finite non-singular affine snapshots are materialized into device
 * geometry. Legacy snapshots never become authority for a new route.
 */
internal fun ClipStack.toGPUClipFacts(target: GPUTargetFacts): GPUClipFacts = when (this) {
    ClipStack.WideOpen -> GPUClipFacts.wideOpen(target.bounds())
    is ClipStack.DeviceRect -> {
        // DeviceRect has no captured transform. Preserve malformed historical input for
        // its owning lowerer to diagnose rather than misclassifying it as a typed
        // transform-projection refusal.
        val element = GPUClipCoverageElement(
            operation = GPUClipCoverageOperation.Intersect,
            kind = GPUClipCoverageElementKind.Rect,
            values = listOf(rect.left, rect.top, rect.right, rect.bottom),
            vertexCount = 0,
            antiAlias = antiAlias,
            fillRule = GPUClipFillRule.Winding,
            inverseFill = false,
        )
        GPUClipFacts(
            kind = GPUClipKind.DeviceRect,
            bounds = GPUBounds(rect.left, rect.top, rect.right, rect.bottom),
            coverageRequest = GPUClipCoverageRequest(
                targetWidth = target.width,
                targetHeight = target.height,
                elements = listOf(element),
                scissorEligible = !antiAlias && rect.isIntegerAligned(),
            ),
            perspectiveCaptureRefusal = perspectiveCaptureRefusal,
        )
    }
    is ClipStack.Complex -> when (val transition = transitionalElements()) {
        is TransitionalClipElements.Ready -> GPUClipFacts(
            kind = GPUClipKind.ComplexStack,
            bounds = target.bounds(),
            coverageRequest = GPUClipCoverageRequest(
                targetWidth = target.width,
                targetHeight = target.height,
                elements = transition.elements,
            ),
        )
        is TransitionalClipElements.Refused -> GPUClipFacts(
            kind = GPUClipKind.ComplexStack,
            bounds = target.bounds(),
            perspectiveCaptureRefusal = transition.reason == TRANSITIONAL_CLIP_PERSPECTIVE,
            clipTransformRefusal = transition.reason,
        )
    }
}

private fun GPUTargetFacts.bounds(): GPUBounds =
    GPUBounds(0f, 0f, width.toFloat(), height.toFloat())

private sealed interface TransitionalClipElements {
    data class Ready(val elements: List<GPUClipCoverageElement>) : TransitionalClipElements
    data class Refused(val reason: String) : TransitionalClipElements
}

private fun ClipStack.Complex.transitionalElements(): TransitionalClipElements {
    val elements = ArrayList<GPUClipCoverageElement>(ops.size)
    for (op in ops) {
        when (val element = op.transitionalElement()) {
            is TransitionalClipElement.Ready -> elements += element.element
            is TransitionalClipElement.Refused -> return TransitionalClipElements.Refused(element.reason)
        }
    }
    return TransitionalClipElements.Ready(elements)
}

/** Stable refusal used by every typed consumer before legacy coverage planning. */
internal fun ClipStack.typedClipTransformRefusalOrNull(): String? = when (this) {
    ClipStack.WideOpen,
    is ClipStack.DeviceRect,
    -> null
    is ClipStack.Complex -> (transitionalElements() as? TransitionalClipElements.Refused)?.reason
}

private sealed interface TransitionalClipElement {
    data class Ready(val element: GPUClipCoverageElement) : TransitionalClipElement
    data class Refused(val reason: String) : TransitionalClipElement
}

private fun ClipStackOp.transitionalElement(): TransitionalClipElement {
    val known = transform as? ClipTransformSnapshot.Known
        ?: return TransitionalClipElement.Refused(
            transform.transitionalClipRefusalOrNull() ?: TRANSITIONAL_CLIP_LEGACY_UNAVAILABLE,
        )
    val matrix = known.copyMatrixF32()
    matrix.transitionalClipRefusalOrNull()?.let { return TransitionalClipElement.Refused(it) }
    val transformClass = matrix.legacyBoundaryTransformClass()
    return try {
        when (this) {
            is ClipStackOp.RectOp -> if (matrix.isScaleTranslate()) {
                rect.toClipElement(op, antiAlias, matrix.mapAxisAlignedRect(rect))
            } else {
                Path().addRect(rect).transform(matrix).toPathClipElement(op, antiAlias, transformClass)
            }
            is ClipStackOp.RRectOp -> if (matrix.isScaleTranslate()) {
                rrect.toRRectClipElement(op, antiAlias, transformClass, rrect.mapAxisAligned(matrix))
            } else {
                Path().addRRect(rrect).transform(matrix).toPathClipElement(op, antiAlias, transformClass)
            }
            is ClipStackOp.PathOp -> path.transform(matrix).toPathClipElement(op, antiAlias, transformClass)
        }.let { element ->
            element?.let(TransitionalClipElement::Ready)
                ?: TransitionalClipElement.Refused(TRANSITIONAL_CLIP_NONFINITE_PROJECTION)
        }
    } catch (_: IllegalArgumentException) {
        TransitionalClipElement.Refused(TRANSITIONAL_CLIP_NONFINITE_PROJECTION)
    }
}

private fun org.graphiks.math.geometry.RectF32.toClipElement(
    op: ClipOp,
    antiAlias: Boolean,
    mapped: org.graphiks.math.geometry.RectF32 = this,
): GPUClipCoverageElement? {
    val values = listOf(mapped.left, mapped.top, mapped.right, mapped.bottom)
    if (values.any { !it.isFinite() }) return null
    return GPUClipCoverageElement(
        operation = op.toCoverageOperation(),
        kind = GPUClipCoverageElementKind.Rect,
        values = values,
        vertexCount = 0,
        antiAlias = antiAlias,
        fillRule = GPUClipFillRule.Winding,
        inverseFill = false,
    )
}

private fun org.graphiks.math.geometry.RRectF32.toRRectClipElement(
    op: ClipOp,
    antiAlias: Boolean,
    transformClass: String,
    mapped: org.graphiks.math.geometry.RRectF32 = this,
): GPUClipCoverageElement? {
    val values = listOf(
        mapped.rect.left, mapped.rect.top, mapped.rect.right, mapped.rect.bottom,
        mapped.topLeft.x, mapped.topLeft.y,
        mapped.topRight.x, mapped.topRight.y,
        mapped.bottomRight.x, mapped.bottomRight.y,
        mapped.bottomLeft.x, mapped.bottomLeft.y,
    )
    if (values.any { !it.isFinite() }) return null
    return GPUClipCoverageElement(
        operation = op.toCoverageOperation(),
        kind = GPUClipCoverageElementKind.RRect,
        values = values,
        vertexCount = 0,
        antiAlias = antiAlias,
        fillRule = GPUClipFillRule.Winding,
        inverseFill = false,
        transformClass = transformClass,
    )
}

private fun Path.toPathClipElement(
    op: ClipOp,
    antiAlias: Boolean,
    transformClass: String,
): GPUClipCoverageElement? {
    val flattened = PathTessellator(
        tolerance = 0.25f,
        maxVertices = Int.MAX_VALUE,
    ).flattenWithContours(toPathTessellatorData())
    if (flattened.points.any { point -> !point.x.isFinite() || !point.y.isFinite() }) return null
    val fill = fillType.toClipFill()
    return GPUClipCoverageElement(
        operation = op.toCoverageOperation(),
        kind = GPUClipCoverageElementKind.Path,
        values = buildList {
            add(flattened.contourStarts.size.toFloat())
            flattened.contourStarts.forEach { add(it.toFloat()) }
            flattened.points.forEach { point ->
                add(point.x)
                add(point.y)
            }
        },
        vertexCount = flattened.points.size,
        antiAlias = antiAlias,
        fillRule = fill.rule,
        inverseFill = fill.inverse,
        transformClass = transformClass,
        hasCubicSegments = commands().any { command -> command is PathCommand.Cubic },
    )
}

private const val TRANSITIONAL_CLIP_NONFINITE = "unsupported_clip_transform:NonFinite"
private const val TRANSITIONAL_CLIP_PERSPECTIVE = "unsupported_transform:Perspective"
private const val TRANSITIONAL_CLIP_SINGULAR = "unsupported_clip_transform:Singular"
private const val TRANSITIONAL_CLIP_NONFINITE_PROJECTION = "unsupported_clip_transform:NonFiniteProjection"
private const val TRANSITIONAL_CLIP_LEGACY_UNAVAILABLE = "unsupported_clip_transform:LegacyUnavailable"

/** Stable refusal used by every typed consumer before legacy coverage planning. */
internal fun ClipTransformSnapshot.transitionalClipRefusalOrNull(): String? = when (this) {
    is ClipTransformSnapshot.Known -> copyMatrixF32().transitionalClipRefusalOrNull()
    is ClipTransformSnapshot.LegacyUnavailable -> if (perspectiveCaptureRefusal) {
        TRANSITIONAL_CLIP_PERSPECTIVE
    } else {
        TRANSITIONAL_CLIP_LEGACY_UNAVAILABLE
    }
}

private fun Matrix3x3F32.transitionalClipRefusalOrNull(): String? {
    if (!listOf(sx, kx, tx, ky, sy, ty, persp0, persp1, persp2).all(Float::isFinite)) {
        return TRANSITIONAL_CLIP_NONFINITE
    }
    if (hasPerspective()) return TRANSITIONAL_CLIP_PERSPECTIVE
    val determinant = sx.toDouble() * sy.toDouble() - kx.toDouble() * ky.toDouble()
    return when {
        !determinant.isFinite() -> TRANSITIONAL_CLIP_NONFINITE
        determinant == 0.0 -> TRANSITIONAL_CLIP_SINGULAR
        else -> null
    }
}

/** Provenance is derived from [Matrix3x3F32], never from a legacy string snapshot. */
private fun Matrix3x3F32.legacyBoundaryTransformClass(): String = when {
    this == Matrix3x3F32.Identity -> "identity"
    (sx == 0f && sy == 0f && kx == -1f && ky == 1f) ||
        (sx == -1f && sy == -1f && kx == 0f && ky == 0f) -> "right-angle-rotation"
    kx == 0f && ky == 0f && sx == 1f && sy == 1f -> "translate"
    kx == 0f && ky == 0f && sx == sy && sx > 0f -> "uniform-positive-scale-translate"
    kx == 0f && ky == 0f && tx == 0f && ty == 0f -> "scale"
    kx == 0f && ky == 0f -> "scale-translate"
    else -> "affine"
}

private fun ClipOp.toCoverageOperation(): GPUClipCoverageOperation = when (this) {
    ClipOp.INTERSECT -> GPUClipCoverageOperation.Intersect
    ClipOp.DIFFERENCE -> GPUClipCoverageOperation.Difference
}

private data class ClipFill(val rule: GPUClipFillRule, val inverse: Boolean)

private fun FillType.toClipFill(): ClipFill = when (this) {
    FillType.WINDING -> ClipFill(GPUClipFillRule.Winding, inverse = false)
    FillType.INVERSE_WINDING -> ClipFill(GPUClipFillRule.Winding, inverse = true)
    FillType.EVEN_ODD -> ClipFill(GPUClipFillRule.EvenOdd, inverse = false)
    FillType.INVERSE_EVEN_ODD -> ClipFill(GPUClipFillRule.EvenOdd, inverse = true)
}

private fun org.graphiks.math.geometry.RectF32.isIntegerAligned(): Boolean =
    listOf(left, top, right, bottom).all { it == it.toInt().toFloat() }

/** Converts a Kanvas path to the existing tessellator transport without losing contours. */
internal fun Path.toPathTessellatorData(): PathData {
    val verbs = mutableListOf<GpuPathVerb>()
    val points = mutableListOf<Point>()
    var currentPoint = Point(0f, 0f)
    var contourStart = currentPoint
    for (command in commands()) {
        when (command) {
            is PathCommand.Move -> {
                currentPoint = Point(command.point.x, command.point.y)
                contourStart = currentPoint
                verbs.add(GpuPathVerb.MoveTo(currentPoint))
            }
            is PathCommand.Line -> {
                currentPoint = Point(command.endpoint.x, command.endpoint.y)
                verbs.add(GpuPathVerb.LineTo(currentPoint))
            }
            is PathCommand.Quad -> {
                currentPoint = Point(command.endpoint.x, command.endpoint.y)
                verbs.add(GpuPathVerb.QuadTo(Point(command.control.x, command.control.y), currentPoint))
            }
            is PathCommand.Cubic -> {
                currentPoint = Point(command.endpoint.x, command.endpoint.y)
                verbs.add(
                    GpuPathVerb.CubicTo(
                        Point(command.control1.x, command.control1.y),
                        Point(command.control2.x, command.control2.y),
                        currentPoint,
                    ),
                )
            }
            is PathCommand.ArcTo -> {
                val arcEndpoint = Point(command.endpoint.x, command.endpoint.y)
                flattenSvgArc(
                    start = currentPoint,
                    radius = Point(command.radius.x, command.radius.y),
                    xAxisRotation = command.xAxisRotation,
                    largeArc = command.largeArc,
                    sweep = command.sweep,
                    endpoint = arcEndpoint,
                ).forEach { verbs.add(GpuPathVerb.LineTo(it)) }
                currentPoint = arcEndpoint
            }
            PathCommand.Close -> {
                verbs.add(GpuPathVerb.Close)
                currentPoint = contourStart
            }
        }
    }
    return PathData(verbs = verbs, points = points)
}

private fun flattenSvgArc(
    start: Point,
    radius: Point,
    xAxisRotation: Float,
    largeArc: Boolean,
    sweep: Boolean,
    endpoint: Point,
): List<Point> {
    if (!endpoint.x.isFinite() || !endpoint.y.isFinite()) return emptyList()
    if (!start.x.isFinite() || !start.y.isFinite() || !radius.x.isFinite() || !radius.y.isFinite() || !xAxisRotation.isFinite()) {
        return listOf(endpoint)
    }
    val startX = start.x.toDouble()
    val startY = start.y.toDouble()
    val endX = endpoint.x.toDouble()
    val endY = endpoint.y.toDouble()
    var rx = abs(radius.x.toDouble())
    var ry = abs(radius.y.toDouble())
    if (rx == 0.0 || ry == 0.0 || (startX == endX && startY == endY)) return listOf(endpoint)

    val rotation = (xAxisRotation.toDouble() % 360.0) * PI / 180.0
    val cosRotation = cos(rotation)
    val sinRotation = sin(rotation)
    val halfDx = (startX - endX) / 2.0
    val halfDy = (startY - endY) / 2.0
    val transformedX = cosRotation * halfDx + sinRotation * halfDy
    val transformedY = -sinRotation * halfDx + cosRotation * halfDy
    val radiiScale = transformedX * transformedX / (rx * rx) + transformedY * transformedY / (ry * ry)
    if (!radiiScale.isFinite()) return listOf(endpoint)
    if (radiiScale > 1.0) {
        val scale = sqrt(radiiScale)
        rx *= scale
        ry *= scale
    }

    val rxSquared = rx * rx
    val rySquared = ry * ry
    val transformedXSquared = transformedX * transformedX
    val transformedYSquared = transformedY * transformedY
    val centerDenominator = rxSquared * transformedYSquared + rySquared * transformedXSquared
    if (centerDenominator <= 0.0 || !centerDenominator.isFinite()) return listOf(endpoint)
    val centerNumerator = maxOf(0.0, rxSquared * rySquared - rxSquared * transformedYSquared - rySquared * transformedXSquared)
    val centerScale = if (largeArc == sweep) -1.0 else 1.0
    val centerFactor = centerScale * sqrt(centerNumerator / centerDenominator)
    if (!centerFactor.isFinite()) return listOf(endpoint)

    val centerTransformedX = centerFactor * rx * transformedY / ry
    val centerTransformedY = -centerFactor * ry * transformedX / rx
    val centerX = cosRotation * centerTransformedX - sinRotation * centerTransformedY + (startX + endX) / 2.0
    val centerY = sinRotation * centerTransformedX + cosRotation * centerTransformedY + (startY + endY) / 2.0
    val startVectorX = (transformedX - centerTransformedX) / rx
    val startVectorY = (transformedY - centerTransformedY) / ry
    val endVectorX = (-transformedX - centerTransformedX) / rx
    val endVectorY = (-transformedY - centerTransformedY) / ry
    val startAngle = atan2(startVectorY, startVectorX)
    var sweepAngle = atan2(
        startVectorX * endVectorY - startVectorY * endVectorX,
        startVectorX * endVectorX + startVectorY * endVectorY,
    )
    if (!sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
    if (sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI
    if (!startAngle.isFinite() || !sweepAngle.isFinite()) return listOf(endpoint)

    val flatnessTolerance = 0.25
    val subdivisionRadius = maxOf(rx, ry)
    val maxSegmentAngle = if (subdivisionRadius <= flatnessTolerance) PI else {
        2.0 * acos(((subdivisionRadius - flatnessTolerance).coerceAtLeast(0.0) / subdivisionRadius).coerceIn(0.0, 1.0))
    }
    val segmentCount = if (maxSegmentAngle.isFinite() && maxSegmentAngle > 0.0) {
        ceil(abs(sweepAngle) / maxSegmentAngle).coerceIn(1.0, 64.0).toInt()
    } else {
        64
    }
    return ArrayList<Point>(segmentCount).also { flattened ->
        for (segment in 1..segmentCount) {
            if (segment == segmentCount) {
                flattened.add(endpoint)
                continue
            }
            val angle = startAngle + sweepAngle * segment / segmentCount
            val ellipseX = rx * cos(angle)
            val ellipseY = ry * sin(angle)
            val x = (centerX + cosRotation * ellipseX - sinRotation * ellipseY).toFloat()
            val y = (centerY + sinRotation * ellipseX + cosRotation * ellipseY).toFloat()
            if (!x.isFinite() || !y.isFinite()) return listOf(endpoint)
            flattened.add(Point(x, y))
        }
    }
}
