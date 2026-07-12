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
import org.graphiks.kanvas.geometry.PathVerb
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

/** Maps captured device-space clip state into the immutable GPU coverage transport. */
internal fun ClipStack.toGPUClipFacts(target: GPUTargetFacts): GPUClipFacts = when (this) {
    ClipStack.WideOpen -> GPUClipFacts.wideOpen(target.bounds())
    is ClipStack.DeviceRect -> {
        val element = rect.toClipElement(ClipOp.INTERSECT, antiAlias)
        GPUClipFacts(
            kind = GPUClipKind.DeviceRect,
            bounds = GPUBounds(rect.left, rect.top, rect.right, rect.bottom),
            coverageRequest = GPUClipCoverageRequest(
                targetWidth = target.width,
                targetHeight = target.height,
                elements = listOf(element),
                scissorEligible = !antiAlias && rect.isIntegerAligned(),
            ),
        )
    }
    is ClipStack.Complex -> GPUClipFacts(
        kind = GPUClipKind.ComplexStack,
        bounds = target.bounds(),
        coverageRequest = GPUClipCoverageRequest(
            targetWidth = target.width,
            targetHeight = target.height,
            elements = ops.map(ClipStackOp::toClipElement),
        ),
    )
}

private fun GPUTargetFacts.bounds(): GPUBounds =
    GPUBounds(0f, 0f, width.toFloat(), height.toFloat())

private fun org.graphiks.kanvas.types.Rect.toClipElement(
    op: ClipOp,
    antiAlias: Boolean,
): GPUClipCoverageElement = GPUClipCoverageElement(
    operation = op.toCoverageOperation(),
    kind = GPUClipCoverageElementKind.Rect,
    values = listOf(left, top, right, bottom),
    vertexCount = 0,
    antiAlias = antiAlias,
    fillRule = GPUClipFillRule.Winding,
    inverseFill = false,
)

private fun ClipStackOp.toClipElement(): GPUClipCoverageElement = when (this) {
    is ClipStackOp.RectOp -> rect.toClipElement(op, antiAlias)
    is ClipStackOp.RRectOp -> GPUClipCoverageElement(
        operation = op.toCoverageOperation(),
        kind = GPUClipCoverageElementKind.RRect,
        values = listOf(
            rrect.rect.left, rrect.rect.top, rrect.rect.right, rrect.rect.bottom,
            rrect.topLeft.x, rrect.topLeft.y,
            rrect.topRight.x, rrect.topRight.y,
            rrect.bottomRight.x, rrect.bottomRight.y,
            rrect.bottomLeft.x, rrect.bottomLeft.y,
        ),
        vertexCount = 0,
        antiAlias = antiAlias,
        fillRule = GPUClipFillRule.Winding,
        inverseFill = false,
    )
    is ClipStackOp.PathOp -> {
        val flattened = PathTessellator(tolerance = 0.25f).flattenWithContours(path.toPathTessellatorData())
        val fill = path.fillType.toClipFill()
        GPUClipCoverageElement(
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
        )
    }
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

private fun org.graphiks.kanvas.types.Rect.isIntegerAligned(): Boolean =
    listOf(left, top, right, bottom).all { it == it.toInt().toFloat() }

/** Converts a Kanvas path to the existing tessellator transport without losing contours. */
internal fun Path.toPathTessellatorData(): PathData {
    val verbs = mutableListOf<GpuPathVerb>()
    val points = mutableListOf<Point>()
    val kanvasVerbs = this.verbs()
    val kanvasPoints = this.points()
    var pointIndex = 0
    var currentPoint = Point(0f, 0f)
    var contourStart = currentPoint
    for (verb in kanvasVerbs) {
        when (verb) {
            PathVerb.MOVE -> {
                val point = kanvasPoints[pointIndex++]
                currentPoint = Point(point.x, point.y)
                contourStart = currentPoint
                verbs.add(GpuPathVerb.MoveTo(currentPoint))
            }
            PathVerb.LINE -> {
                val point = kanvasPoints[pointIndex++]
                currentPoint = Point(point.x, point.y)
                verbs.add(GpuPathVerb.LineTo(currentPoint))
            }
            PathVerb.QUAD -> {
                val control = kanvasPoints[pointIndex++]
                val point = kanvasPoints[pointIndex++]
                currentPoint = Point(point.x, point.y)
                verbs.add(GpuPathVerb.QuadTo(Point(control.x, control.y), currentPoint))
            }
            PathVerb.CUBIC -> {
                val control1 = kanvasPoints[pointIndex++]
                val control2 = kanvasPoints[pointIndex++]
                val point = kanvasPoints[pointIndex++]
                currentPoint = Point(point.x, point.y)
                verbs.add(GpuPathVerb.CubicTo(Point(control1.x, control1.y), Point(control2.x, control2.y), currentPoint))
            }
            PathVerb.ARC_TO -> {
                val radius = kanvasPoints[pointIndex++]
                val rotationAndLargeArc = kanvasPoints[pointIndex++]
                val sweep = kanvasPoints[pointIndex++]
                val endpoint = kanvasPoints[pointIndex++]
                val arcEndpoint = Point(endpoint.x, endpoint.y)
                flattenSvgArc(
                    start = currentPoint,
                    radius = Point(radius.x, radius.y),
                    xAxisRotation = rotationAndLargeArc.x,
                    largeArc = rotationAndLargeArc.y > 0f,
                    sweep = sweep.x > 0f,
                    endpoint = arcEndpoint,
                ).forEach { verbs.add(GpuPathVerb.LineTo(it)) }
                currentPoint = arcEndpoint
            }
            PathVerb.CLOSE -> {
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
