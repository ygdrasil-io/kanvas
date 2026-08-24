package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.dsl.PathScope
import org.graphiks.kanvas.types.Line
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.vector.Vector2F32
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Path internal constructor() {
    var fillType: FillType = FillType.WINDING

    private val commands = mutableListOf<PathCommand>()

    fun moveTo(x: Float, y: Float): Path {
        commands.add(PathCommand.Move(Point2F32(x, y)))
        return this
    }
    fun lineTo(x: Float, y: Float): Path {
        commands.add(PathCommand.Line(Point2F32(x, y)))
        return this
    }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path {
        commands.add(PathCommand.Quad(Point2F32(cx, cy), Point2F32(x, y)))
        return this
    }
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Path {
        commands.add(PathCommand.Cubic(Point2F32(cx1, cy1), Point2F32(cx2, cy2), Point2F32(x, y)))
        return this
    }
    fun arcTo(rx: Float, ry: Float, xAxisRotation: Float, largeArc: Boolean, sweep: Boolean, x: Float, y: Float): Path {
        commands.add(
            PathCommand.ArcTo(
                radius = Vector2F32(rx, ry),
                xAxisRotation = xAxisRotation,
                largeArc = largeArc,
                sweep = sweep,
                endpoint = Point2F32(x, y),
            ),
        )
        return this
    }
    fun close(): Path { commands.add(PathCommand.Close); return this }

    fun addRect(rect: RectF32): Path {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
        return this
    }

    fun addOval(rect: RectF32): Path {
        val cx = rect.center().x; val cy = rect.center().y
        val rx = rect.width() / 2f; val ry = rect.height() / 2f
        val k = 0.5522847498f
        moveTo(cx + rx, cy)
        cubicTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry)
        cubicTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy)
        cubicTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry)
        cubicTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy)
        close()
        return this
    }

    fun addCircle(cx: Float, cy: Float, r: Float): Path = addOval(RectF32.ofLTRB(cx - r, cy - r, cx + r, cy + r))

    fun addRRect(rrect: RRect): Path {
        val r = rrect.rect
        val (tl, tr, br, bl) = normalizedRadii(rrect)
        moveTo(r.left + tl.x, r.top)
        lineTo(r.right - tr.x, r.top)
        arcTo(tr.x, tr.y, 0f, false, true, r.right, r.top + tr.y)
        lineTo(r.right, r.bottom - br.y)
        arcTo(br.x, br.y, 0f, false, true, r.right - br.x, r.bottom)
        lineTo(r.left + bl.x, r.bottom)
        arcTo(bl.x, bl.y, 0f, false, true, r.left, r.bottom - bl.y)
        lineTo(r.left, r.top + tl.y)
        arcTo(tl.x, tl.y, 0f, false, true, r.left + tl.x, r.top)
        close()
        return this
    }

    private fun normalizedRadii(rrect: RRect): Array<CornerRadii> {
        val width = rrect.rect.width().coerceAtLeast(0f)
        val height = rrect.rect.height().coerceAtLeast(0f)
        val tl = rrect.topLeft.nonNegative()
        val tr = rrect.topRight.nonNegative()
        val br = rrect.bottomRight.nonNegative()
        val bl = rrect.bottomLeft.nonNegative()
        val scale = min(
            1f,
            min(
                ratioOrOne(width, tl.x + tr.x),
                min(
                    ratioOrOne(width, bl.x + br.x),
                    min(
                        ratioOrOne(height, tl.y + bl.y),
                        ratioOrOne(height, tr.y + br.y),
                    ),
                ),
            ),
        )
        return arrayOf(tl.scaled(scale), tr.scaled(scale), br.scaled(scale), bl.scaled(scale))
    }

    private fun ratioOrOne(limit: Float, sum: Float): Float =
        if (sum > limit && sum > 0f) limit / sum else 1f

    private fun CornerRadii.nonNegative(): CornerRadii =
        CornerRadii(x.coerceAtLeast(0f), y.coerceAtLeast(0f))

    private fun CornerRadii.scaled(scale: Float): CornerRadii =
        CornerRadii(x * scale, y * scale)

    fun addPath(path: Path): Path {
        commands.addAll(path.commands)
        return this
    }

    fun reverseAddPath(src: Path): Path {
        var contourStart = 0
        while (contourStart < src.commands.size) {
            val move = src.commands[contourStart] as? PathCommand.Move
            if (move == null) {
                contourStart++
                continue
            }
            var contourEnd = contourStart + 1
            while (contourEnd < src.commands.size && src.commands[contourEnd] !is PathCommand.Move) {
                contourEnd++
            }
            val contour = src.commands.subList(contourStart, contourEnd)
            val hasClose = contour.lastOrNull() is PathCommand.Close
            val drawable = if (hasClose) contour.dropLast(1) else contour
            val lastEnd = drawable.lastOrNull()?.endpoint ?: move.point
            moveTo(lastEnd.x, lastEnd.y)

            for (index in drawable.lastIndex downTo 1) {
                val command = drawable[index]
                val start = drawable[index - 1].endpoint ?: move.point
                when (command) {
                    is PathCommand.Line -> lineTo(start.x, start.y)
                    is PathCommand.Quad -> quadTo(command.control.x, command.control.y, start.x, start.y)
                    is PathCommand.Cubic -> cubicTo(
                        command.control2.x, command.control2.y,
                        command.control1.x, command.control1.y,
                        start.x, start.y,
                    )
                    is PathCommand.ArcTo -> arcTo(
                        command.radius.x,
                        command.radius.y,
                        command.xAxisRotation,
                        command.largeArc,
                        !command.sweep,
                        start.x,
                        start.y,
                    )
                    else -> Unit
                }
            }
            if (hasClose) close()
            contourStart = contourEnd
        }

        return this
    }

    fun isEmpty(): Boolean = commands.isEmpty()

    /**
     * Returns a conservative axis-aligned bound for the path's drawable
     * geometry, or `null` when it has no points. Bézier control points are
     * included, so the result contains the ink even when it is not tight.
     */
    fun computeBounds(): RectF32? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        fun include(point: Point2F32) {
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }
        for (command in commands) {
            when (command) {
                is PathCommand.Move, is PathCommand.Line -> include(command.endpoint!!)
                is PathCommand.Quad -> {
                    include(command.control)
                    include(command.endpoint)
                }
                is PathCommand.Cubic -> {
                    include(command.control1)
                    include(command.control2)
                    include(command.endpoint)
                }
                is PathCommand.ArcTo -> include(command.endpoint)
                PathCommand.Close -> Unit
            }
        }
        return if (minX.isFinite()) RectF32.ofLTRB(minX, minY, maxX, maxY) else null
    }

    fun isRect(rect: RectF32? = null): Boolean {
        if (commands.size < 5) return false
        val move = commands[0] as? PathCommand.Move ?: return false
        val hasClose = commands.last() is PathCommand.Close
        val lineCount = if (hasClose) commands.size - 2 else commands.size - 1
        if (lineCount < 3 || lineCount > 4) return false
        val endIdx = if (hasClose) commands.size - 1 else commands.size
        for (i in 1 until endIdx) {
            if (commands[i] !is PathCommand.Line) return false
        }
        val corners = listOf(
            move.point,
            (commands[1] as PathCommand.Line).endpoint,
            (commands[2] as PathCommand.Line).endpoint,
            (commands[3] as PathCommand.Line).endpoint,
        )
        val closePt = if (lineCount == 4) (commands[4] as PathCommand.Line).endpoint else move.point
        // Verify the path is closed: last point must match first point
        if (lineCount == 4 && closePt != move.point) return false
        for (i in 0..3) {
            val next = if (i == 3) closePt else corners[i + 1]
            val dx = next.x - corners[i].x
            val dy = next.y - corners[i].y
            if (dx != 0f && dy != 0f) return false
        }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0..3) {
            minX = minOf(minX, corners[i].x); minY = minOf(minY, corners[i].y)
            maxX = maxOf(maxX, corners[i].x); maxY = maxOf(maxY, corners[i].y)
        }
        rect?.let { r ->
            r.left = minX; r.top = minY; r.right = maxX; r.bottom = maxY
        }
        return true
    }

    fun isOval(bounds: RectF32? = null): Boolean {
        if (commands.size < 5) return false
        val move = commands[0] as? PathCommand.Move ?: return false
        val hasClose = commands.last() is PathCommand.Close
        val cubicCount = commands.size - 1 - (if (hasClose) 1 else 0)
        if (cubicCount != 4) return false
        for (i in 1 until commands.size - (if (hasClose) 1 else 0)) {
            if (commands[i] !is PathCommand.Cubic) return false
        }
        val endpoints = listOf(
            move.point,
            (commands[1] as PathCommand.Cubic).endpoint,
            (commands[2] as PathCommand.Cubic).endpoint,
            (commands[3] as PathCommand.Cubic).endpoint,
            (commands[4] as PathCommand.Cubic).endpoint,
        )
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (point in endpoints) {
            minX = minOf(minX, point.x); minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x); maxY = maxOf(maxY, point.y)
        }
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f; val ry = (maxY - minY) / 2f
        if (rx < 1e-6f || ry < 1e-6f) return false
        bounds?.let { b ->
            b.left = minX; b.top = minY; b.right = maxX; b.bottom = maxY
        }
        return true
    }

    fun isRRect(rrect: RRect? = null): Boolean {
        if (commands.size < 9) return false
        if (commands[0] !is PathCommand.Move) return false
        val hasClose = commands.last() is PathCommand.Close
        val checkCount = if (hasClose) commands.size - 1 else commands.size
        if (checkCount != 9) return false
        for (i in 1 until checkCount) {
            if (i % 2 == 1 && commands[i] !is PathCommand.Line) return false
            if (i % 2 == 0 && commands[i] !is PathCommand.ArcTo) return false
        }
        if (rrect != null) {
            val pts = commands.mapNotNull { it.endpoint }
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (pt in pts) {
                minX = minOf(minX, pt.x); minY = minOf(minY, pt.y)
                maxX = maxOf(maxX, pt.x); maxY = maxOf(maxY, pt.y)
            }
            rrect.rect.left = minX; rrect.rect.top = minY
            rrect.rect.right = maxX; rrect.rect.bottom = maxY
        }
        return true
    }

    fun isLine(line: Line? = null): Boolean {
        if (commands.size != 2) return false
        val start = commands[0] as? PathCommand.Move ?: return false
        val end = commands[1] as? PathCommand.Line ?: return false
        line?.let {
            it.p1 = start.point
            it.p2 = end.endpoint
        }
        return true
    }

    fun isConvex(): Boolean {
        val poly = mutableListOf<Point2F32>()
        var firstPt: Point2F32? = null
        var contourCount = 0
        for (command in commands) {
            when (command) {
                is PathCommand.Move -> {
                    if (contourCount > 0 && poly.size >= 3) return false // multi-contour: cannot determine convexity
                    poly.clear(); firstPt = command.point
                    poly.add(command.point); contourCount++
                }
                is PathCommand.Line -> poly.add(command.endpoint)
                PathCommand.Close -> firstPt?.let { poly.add(it) }
                else -> Unit
            }
        }
        if (poly.size < 3) return true
        var sign = 0f
        val n = poly.size - 1
        for (i in 0 until n) {
            val dx1 = poly[(i + 1) % n].x - poly[i].x
            val dy1 = poly[(i + 1) % n].y - poly[i].y
            val dx2 = poly[(i + 2) % n].x - poly[(i + 1) % n].x
            val dy2 = poly[(i + 2) % n].y - poly[(i + 1) % n].y
            val cross = dx1 * dy2 - dy1 * dx2
            if (cross != 0f) {
                if (sign == 0f) sign = cross
                else if (cross * sign < 0f) return false
            }
        }
        return true
    }

    fun isInterpolatable(other: Path): Boolean {
        val v1 = verbs(); val v2 = other.verbs()
        if (v1.size != v2.size) return false
        for (i in v1.indices) {
            if (v1[i] != v2[i]) return false
        }
        return true
    }

    fun contains(point: Point2F32): Boolean {
        if (commands.isEmpty()) return false
        val px = point.x; val py = point.y
        var winding = 0
        val segments = collectSegments()
        for (seg in segments) {
            val (x1, y1, x2, y2) = seg
            if (y1 == y2) continue
            if (py < minOf(y1, y2) || py >= maxOf(y1, y2)) continue
            val xIntersect = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
            if (xIntersect > px) {
                if (y2 > y1) winding++ else winding--
            }
        }
        return when (fillType) {
            FillType.WINDING, FillType.INVERSE_WINDING -> winding != 0
            FillType.EVEN_ODD, FillType.INVERSE_EVEN_ODD -> winding % 2 != 0
        }
    }

    fun conservativelyContainsRect(rect: RectF32): Boolean {
        val tl = Point2F32(rect.left, rect.top)
        val tr = Point2F32(rect.right, rect.top)
        val bl = Point2F32(rect.left, rect.bottom)
        val br = Point2F32(rect.right, rect.bottom)
        return contains(tl) && contains(tr) && contains(bl) && contains(br)
    }

    fun transform(tx: Float, ty: Float, sx: Float, sy: Float): Path {
        val m = Matrix3x3F32.translation(tx, ty) * Matrix3x3F32.scaling(sx, sy)
        return transform(m)
    }

    fun transform(matrix: Matrix3x3F32): Path {
        val result = Path()
        result.fillType = fillType
        for (command in commands) {
            when (command) {
                is PathCommand.Move -> result.commands.add(PathCommand.Move(matrix.transform(command.point)))
                is PathCommand.Line -> result.commands.add(PathCommand.Line(matrix.transform(command.endpoint)))
                is PathCommand.Quad -> result.commands.add(
                    PathCommand.Quad(
                        matrix.transform(command.control),
                        matrix.transform(command.endpoint),
                    ),
                )
                is PathCommand.Cubic -> result.commands.add(
                    PathCommand.Cubic(
                        matrix.transform(command.control1),
                        matrix.transform(command.control2),
                        matrix.transform(command.endpoint),
                    ),
                )
                is PathCommand.ArcTo -> {
                    val transformedArc = transformArcMetadata(
                        radius = command.radius,
                        xAxisRotation = command.xAxisRotation,
                        sweep = command.sweep,
                        matrix = matrix,
                    )
                    result.commands.add(
                        PathCommand.ArcTo(
                            radius = Vector2F32(transformedArc.rx, transformedArc.ry),
                            xAxisRotation = transformedArc.xAxisRotation,
                            largeArc = command.largeArc,
                            sweep = transformedArc.sweep,
                            endpoint = matrix.transform(command.endpoint),
                        ),
                    )
                }
                PathCommand.Close -> result.commands.add(PathCommand.Close)
            }
        }
        return result
    }

    private data class TransformedArcMetadata(
        val rx: Float,
        val ry: Float,
        val xAxisRotation: Float,
        val sweep: Boolean,
    )

    private fun transformArcMetadata(
        radius: Vector2F32,
        xAxisRotation: Float,
        sweep: Boolean,
        matrix: Matrix3x3F32,
    ): TransformedArcMetadata {
        val angle = xAxisRotation.toDouble() * PI / 180.0
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val rx = abs(radius.x.toDouble())
        val ry = abs(radius.y.toDouble())
        val xAxisX = cosAngle * rx
        val xAxisY = sinAngle * rx
        val yAxisX = -sinAngle * ry
        val yAxisY = cosAngle * ry
        val transformedXAxisX = matrix.sx * xAxisX + matrix.kx * xAxisY
        val transformedXAxisY = matrix.ky * xAxisX + matrix.sy * xAxisY
        val transformedYAxisX = matrix.sx * yAxisX + matrix.kx * yAxisY
        val transformedYAxisY = matrix.ky * yAxisX + matrix.sy * yAxisY

        val xAxisLengthSquared = transformedXAxisX * transformedXAxisX + transformedXAxisY * transformedXAxisY
        val yAxisLengthSquared = transformedYAxisX * transformedYAxisX + transformedYAxisY * transformedYAxisY
        val axisDot = transformedXAxisX * transformedYAxisX + transformedXAxisY * transformedYAxisY
        val dotTolerance = 1e-6 * sqrt(xAxisLengthSquared * yAxisLengthSquared)
        val (transformedRx, transformedRy, transformedRotation) = if (abs(axisDot) <= dotTolerance) {
            val transformedRx = sqrt(xAxisLengthSquared)
            val transformedRy = sqrt(yAxisLengthSquared)
            val transformedRotation = when {
                transformedRx > 0.0 -> atan2(transformedXAxisY, transformedXAxisX) * 180.0 / PI
                transformedRy > 0.0 -> atan2(-transformedYAxisX, transformedYAxisY) * 180.0 / PI
                else -> xAxisRotation.toDouble()
            }
            Triple(transformedRx, transformedRy, transformedRotation)
        } else {
            val covarianceXX = transformedXAxisX * transformedXAxisX + transformedYAxisX * transformedYAxisX
            val covarianceXY = transformedXAxisX * transformedXAxisY + transformedYAxisX * transformedYAxisY
            val covarianceYY = transformedXAxisY * transformedXAxisY + transformedYAxisY * transformedYAxisY
            val trace = covarianceXX + covarianceYY
            val diff = covarianceXX - covarianceYY
            val root = sqrt(diff * diff + 4.0 * covarianceXY * covarianceXY)
            val major = ((trace + root) / 2.0).coerceAtLeast(0.0)
            val minor = ((trace - root) / 2.0).coerceAtLeast(0.0)
            val transformedRotation = if (major > 0.0) {
                0.5 * atan2(2.0 * covarianceXY, diff) * 180.0 / PI
            } else {
                xAxisRotation.toDouble()
            }
            Triple(sqrt(major), sqrt(minor), transformedRotation)
        }
        val determinant = matrix.sx * matrix.sy - matrix.kx * matrix.ky
        val transformedSweep = if (determinant < 0f) !sweep else sweep
        return TransformedArcMetadata(
            rx = transformedRx.toFloat(),
            ry = transformedRy.toFloat(),
            xAxisRotation = transformedRotation.toFloat(),
            sweep = transformedSweep,
        )
    }

    internal fun commands(): List<PathCommand> = commands.toList()
    internal fun verbs(): List<PathVerb> = commands.map { it.verb }

    private data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    private fun collectSegments(): List<Segment> {
        val result = mutableListOf<Segment>()
        var prevX = 0f; var prevY = 0f
        var firstX = 0f; var firstY = 0f
        var hasPrev = false
        for (command in commands) {
            when (command) {
                is PathCommand.Move -> {
                    firstX = command.point.x; firstY = command.point.y
                    prevX = firstX; prevY = firstY
                    hasPrev = true
                }
                is PathCommand.Line -> {
                    val x = command.endpoint.x; val y = command.endpoint.y
                    result.add(Segment(prevX, prevY, x, y))
                    prevX = x; prevY = y
                }
                is PathCommand.Quad -> {
                    val cx = command.control.x; val cy = command.control.y
                    val x = command.endpoint.x; val y = command.endpoint.y
                    linearizeQuad(prevX, prevY, cx, cy, x, y, result)
                    prevX = x; prevY = y
                }
                is PathCommand.Cubic -> {
                    val cx1 = command.control1.x; val cy1 = command.control1.y
                    val cx2 = command.control2.x; val cy2 = command.control2.y
                    val x = command.endpoint.x; val y = command.endpoint.y
                    linearizeCubic(prevX, prevY, cx1, cy1, cx2, cy2, x, y, result)
                    prevX = x; prevY = y
                }
                is PathCommand.ArcTo -> {
                    val x = command.endpoint.x; val y = command.endpoint.y
                    result.add(Segment(prevX, prevY, x, y))
                    prevX = x; prevY = y
                }
                PathCommand.Close -> {
                    if (hasPrev && (prevX != firstX || prevY != firstY)) {
                        result.add(Segment(prevX, prevY, firstX, firstY))
                        prevX = firstX; prevY = firstY
                    }
                }
            }
        }
        return result
    }

    companion object {
        operator fun invoke(block: PathScope.() -> Unit): Path {
            val scope = PathScope()
            scope.block()
            return scope.build()
        }

        private fun linearizeQuad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, out: MutableList<Segment>) {
            val steps = 8
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val u = 1f - t
                val x = u * u * x0 + 2f * u * t * cx + t * t * x1
                val y = u * u * y0 + 2f * u * t * cy + t * t * y1
                if (i == 1) out.add(Segment(x0, y0, x, y))
                else {
                    val last = out.last()
                    out.add(Segment(last.x2, last.y2, x, y))
                }
            }
        }

        private fun linearizeCubic(x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x1: Float, y1: Float, out: MutableList<Segment>) {
            val steps = 16
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val u = 1f - t
                val x = u * u * u * x0 + 3f * u * u * t * cx1 + 3f * u * t * t * cx2 + t * t * t * x1
                val y = u * u * u * y0 + 3f * u * u * t * cy1 + 3f * u * t * t * cy2 + t * t * t * y1
                if (i == 1) out.add(Segment(x0, y0, x, y))
                else {
                    val last = out.last()
                    out.add(Segment(last.x2, last.y2, x, y))
                }
            }
        }

    }
}

enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }

internal sealed interface PathCommand {
    val verb: PathVerb
    val endpoint: Point2F32?

    data class Move(val point: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.MOVE
        override val endpoint: Point2F32 = point
    }

    data class Line(override val endpoint: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.LINE
    }

    data class Quad(val control: Point2F32, override val endpoint: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.QUAD
    }

    data class Cubic(
        val control1: Point2F32,
        val control2: Point2F32,
        override val endpoint: Point2F32,
    ) : PathCommand {
        override val verb: PathVerb = PathVerb.CUBIC
    }

    data class ArcTo(
        val radius: Vector2F32,
        val xAxisRotation: Float,
        val largeArc: Boolean,
        val sweep: Boolean,
        override val endpoint: Point2F32,
    ) : PathCommand {
        override val verb: PathVerb = PathVerb.ARC_TO
    }

    data object Close : PathCommand {
        override val verb: PathVerb = PathVerb.CLOSE
        override val endpoint: Point2F32? = null
    }
}
