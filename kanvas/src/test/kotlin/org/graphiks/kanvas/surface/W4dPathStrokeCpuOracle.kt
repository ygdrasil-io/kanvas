package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Small test-only W4d pixel oracle for deliberately linear fixtures.
 *
 * It starts from public path/paint facts and owns its line dash, finite-stroke
 * outline, hairline, fill and hard-edge pixel-centre decisions. It deliberately
 * does not depend on planner, renderer, or production stroke preparation.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4dPathStrokeCpuOracle {
    internal data class Draw(
        val path: PathF32,
        val paint: Paint,
        val transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        val scissorI32: RectI32,
    )

    internal fun render(
        widthI32: Int,
        heightI32: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray {
        require(widthI32 > 0 && heightI32 > 0)
        require(draws.size <= MAX_DRAWS_I32)
        val pixels = Array(widthI32 * heightI32) { LinearPremul.Transparent }
        draws.forEach { draw ->
            require(!draw.paint.antiAlias)
            require(draw.transform.isScaleTranslate())
            val sourceContours = lineContours(draw.path)
            val strokeRuns = strokeRuns(sourceContours, draw.paint.pathEffect)
            val source = LinearPremul.from(draw.paint.color)
            for (yI32 in 0 until heightI32) {
                for (xI32 in 0 until widthI32) {
                    if (xI32 !in draw.scissorI32.left until draw.scissorI32.right ||
                        yI32 !in draw.scissorI32.top until draw.scissorI32.bottom
                    ) continue
                    val devicePoint = PointF64(xI32 + 0.5, yI32 + 0.5)
                    val covered = when (draw.paint.style) {
                        PaintStyle.FILL -> containsFill(
                            sourceContours,
                            draw.path.fillRule,
                            inverseMap(draw.transform, devicePoint),
                        )
                        PaintStyle.STROKE -> containsStroke(draw, strokeRuns, devicePoint, hairline = draw.paint.strokeWidth == 0f)
                        PaintStyle.STROKE_AND_FILL -> {
                            val sourcePoint = inverseMap(draw.transform, devicePoint)
                            containsFill(sourceContours, draw.path.fillRule, sourcePoint) ||
                                (draw.paint.strokeWidth > 0f && containsStroke(draw, strokeRuns, devicePoint, hairline = false))
                        }
                    }
                    if (!covered) continue
                    val indexI32 = yI32 * widthI32 + xI32
                    pixels[indexI32] = srcOver(source, pixels[indexI32]).quantizedForAttachment()
                }
            }
        }
        return UByteArray(widthI32 * heightI32 * CHANNELS_PER_PIXEL_I32).also { bytes ->
            pixels.forEachIndexed { pixelIndexI32, color ->
                val offsetI32 = pixelIndexI32 * CHANNELS_PER_PIXEL_I32
                val red = color.red.toSrgbByte()
                val green = color.green.toSrgbByte()
                val blue = color.blue.toSrgbByte()
                when (format) {
                    PixelFormat.RGBA8 -> {
                        bytes[offsetI32] = red
                        bytes[offsetI32 + 1] = green
                        bytes[offsetI32 + 2] = blue
                    }
                    PixelFormat.BGRA8 -> {
                        bytes[offsetI32] = blue
                        bytes[offsetI32 + 1] = green
                        bytes[offsetI32 + 2] = red
                    }
                }
                bytes[offsetI32 + 3] = color.alpha.toQuantizedUByte()
            }
        }
    }

    private fun containsStroke(
        draw: Draw,
        sourceRuns: List<RunF64>,
        devicePoint: PointF64,
        hairline: Boolean,
    ): Boolean {
        val runs = if (hairline) sourceRuns.map { run ->
            RunF64(run.points.map { map(draw.transform, it) }, run.closed)
        } else {
            sourceRuns
        }
        val point = if (hairline) devicePoint else inverseMap(draw.transform, devicePoint)
        val halfWidthF64 = if (hairline) 0.5 else draw.paint.strokeWidth.toDouble() * 0.5
        return runs.any { run -> containsRun(run, point, halfWidthF64, draw.paint.strokeCap, draw.paint.strokeJoin, draw.paint.strokeMiter.toDouble()) }
    }

    private fun containsRun(
        run: RunF64,
        point: PointF64,
        halfWidthF64: Double,
        cap: StrokeCap,
        join: StrokeJoin,
        miterLimitF64: Double,
    ): Boolean {
        if (run.points.size < 2 || halfWidthF64 <= 0.0) return false
        val segments = buildList {
            run.points.zipWithNext().forEach { (start, end) -> if (start != end) add(LineF64(start, end)) }
            if (run.closed && run.points.last() != run.points.first()) add(LineF64(run.points.last(), run.points.first()))
        }
        if (segments.any { containsSegmentBody(it, point, halfWidthF64) }) return true

        val joinIndices = if (run.closed) run.points.indices else 1 until run.points.lastIndex
        joinIndices.forEach { indexI32 ->
            val previous = run.points[if (indexI32 == 0) run.points.lastIndex else indexI32 - 1]
            val vertex = run.points[indexI32]
            val next = run.points[if (indexI32 == run.points.lastIndex) 0 else indexI32 + 1]
            if (containsJoin(previous, vertex, next, point, halfWidthF64, join, miterLimitF64)) return true
        }
        if (run.closed) return false

        val first = run.points.first()
        val second = run.points[1]
        val penultimate = run.points[run.points.lastIndex - 1]
        val last = run.points.last()
        return containsCap(first, second, point, halfWidthF64, cap, start = true) ||
            containsCap(penultimate, last, point, halfWidthF64, cap, start = false)
    }

    private fun containsSegmentBody(line: LineF64, point: PointF64, halfWidthF64: Double): Boolean {
        val vector = line.end - line.start
        val lengthSquaredF64 = vector.dot(vector)
        if (lengthSquaredF64 == 0.0) return false
        val relative = point - line.start
        val projectionF64 = relative.dot(vector) / lengthSquaredF64
        if (projectionF64 < 0.0 || projectionF64 > 1.0) return false
        val distanceSquaredF64 = relative.dot(relative) - projectionF64 * projectionF64 * lengthSquaredF64
        return distanceSquaredF64 <= halfWidthF64 * halfWidthF64
    }

    private fun containsCap(
        first: PointF64,
        second: PointF64,
        point: PointF64,
        halfWidthF64: Double,
        cap: StrokeCap,
        start: Boolean,
    ): Boolean {
        if (cap == StrokeCap.BUTT) return false
        val endpoint = if (start) first else second
        if (cap == StrokeCap.ROUND) return squaredDistance(endpoint, point) <= halfWidthF64 * halfWidthF64
        val direction = normalized(second - first)
        val outward = if (start) direction * -1.0 else direction
        val relative = point - endpoint
        val alongF64 = relative.dot(outward)
        val perpendicularF64 = abs(relative.cross(outward))
        return alongF64 in 0.0..halfWidthF64 && perpendicularF64 <= halfWidthF64
    }

    private fun containsJoin(
        previous: PointF64,
        vertex: PointF64,
        next: PointF64,
        point: PointF64,
        halfWidthF64: Double,
        join: StrokeJoin,
        miterLimitF64: Double,
    ): Boolean {
        val incoming = normalized(vertex - previous)
        val outgoing = normalized(next - vertex)
        val turnF64 = incoming.cross(outgoing)
        if (abs(turnF64) <= EPSILON_F64) return false
        if (join == StrokeJoin.ROUND) return squaredDistance(vertex, point) <= halfWidthF64 * halfWidthF64

        val incomingNormal = PointF64(-incoming.y, incoming.x)
        val outgoingNormal = PointF64(-outgoing.y, outgoing.x)
        val sideF64 = if (turnF64 > 0.0) -1.0 else 1.0
        val firstOuter = vertex + incomingNormal * (sideF64 * halfWidthF64)
        val secondOuter = vertex + outgoingNormal * (sideF64 * halfWidthF64)
        if (join == StrokeJoin.BEVEL) return containsTriangle(point, vertex, firstOuter, secondOuter)

        val intersection = lineIntersection(firstOuter, incoming, secondOuter, outgoing)
            ?: return containsTriangle(point, vertex, firstOuter, secondOuter)
        val ratioF64 = sqrt(squaredDistance(vertex, intersection)) / halfWidthF64
        return if (ratioF64 > miterLimitF64) {
            containsTriangle(point, vertex, firstOuter, secondOuter)
        } else {
            containsTriangle(point, vertex, firstOuter, intersection) ||
                containsTriangle(point, vertex, intersection, secondOuter)
        }
    }

    private fun lineIntersection(first: PointF64, firstDirection: PointF64, second: PointF64, secondDirection: PointF64): PointF64? {
        val denominatorF64 = firstDirection.cross(secondDirection)
        if (abs(denominatorF64) <= EPSILON_F64) return null
        val distanceF64 = (second - first).cross(secondDirection) / denominatorF64
        return first + firstDirection * distanceF64
    }

    private fun containsTriangle(point: PointF64, first: PointF64, second: PointF64, third: PointF64): Boolean {
        val firstSign = (second - first).cross(point - first)
        val secondSign = (third - second).cross(point - second)
        val thirdSign = (first - third).cross(point - third)
        val hasNegative = firstSign < -EPSILON_F64 || secondSign < -EPSILON_F64 || thirdSign < -EPSILON_F64
        val hasPositive = firstSign > EPSILON_F64 || secondSign > EPSILON_F64 || thirdSign > EPSILON_F64
        return !(hasNegative && hasPositive)
    }

    private fun strokeRuns(contours: List<ContourF64>, pathEffect: PathEffect?): List<RunF64> = when (pathEffect) {
        null -> contours.map { RunF64(it.points, it.closed) }
        is PathEffect.Dash -> contours.flatMap { dash(it, pathEffect) }
        else -> error("The W4d oracle only accepts the public Dash path effect")
    }

    private fun dash(contour: ContourF64, dash: PathEffect.Dash): List<RunF64> {
        require(dash.intervals.isNotEmpty() && dash.intervals.size % 2 == 0)
        require(dash.intervals.all { it.isFinite() && it >= 0f })
        val intervals = dash.intervals.map(Float::toDouble)
        val periodF64 = intervals.sum()
        require(periodF64 > 0.0 && dash.phase.isFinite())
        var phaseF64 = ((dash.phase.toDouble() % periodF64) + periodF64) % periodF64
        var intervalIndexI32 = 0
        while (phaseF64 >= intervals[intervalIndexI32] && intervals[intervalIndexI32] > 0.0) {
            phaseF64 -= intervals[intervalIndexI32]
            intervalIndexI32 = (intervalIndexI32 + 1) % intervals.size
        }
        var remainingF64 = intervals[intervalIndexI32] - phaseF64
        val lines = buildList {
            contour.points.zipWithNext().forEach { (start, end) -> if (start != end) add(LineF64(start, end)) }
            if (contour.closed && contour.points.last() != contour.points.first()) {
                add(LineF64(contour.points.last(), contour.points.first()))
            }
        }
        val result = mutableListOf<RunF64>()
        var current: MutableList<PointF64>? = null
        fun finish() {
            current?.takeIf { it.size >= 2 }?.let { result += RunF64(it.toList(), false) }
            current = null
        }
        lines.forEach { line ->
            val vector = line.end - line.start
            val lengthF64 = sqrt(vector.dot(vector))
            var coveredF64 = 0.0
            while (coveredF64 < lengthF64) {
                while (remainingF64 <= EPSILON_F64) {
                    if (intervalIndexI32 % 2 == 0) finish()
                    intervalIndexI32 = (intervalIndexI32 + 1) % intervals.size
                    remainingF64 = intervals[intervalIndexI32]
                }
                val stepF64 = minOf(remainingF64, lengthF64 - coveredF64)
                val start = line.start + vector * (coveredF64 / lengthF64)
                val end = line.start + vector * ((coveredF64 + stepF64) / lengthF64)
                if (intervalIndexI32 % 2 == 0 && stepF64 > EPSILON_F64) {
                    val value = current ?: mutableListOf<PointF64>().also { current = it }
                    if (value.lastOrNull() != start) value += start
                    value += end
                } else {
                    finish()
                }
                coveredF64 += stepF64
                remainingF64 -= stepF64
                require(result.size <= MAX_DASH_RUNS_I32)
            }
        }
        finish()
        return result
    }

    private fun lineContours(path: PathF32): List<ContourF64> {
        val contours = mutableListOf<ContourF64>()
        var points: MutableList<PointF64>? = null
        var current = PointF64(0.0, 0.0)
        var closed = false
        fun finish() {
            points?.takeIf { it.size >= 2 }?.let { contours += ContourF64(it.toList(), closed) }
            require(contours.size <= MAX_CONTOURS_I32)
            points = null
            closed = false
        }
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    finish()
                    current = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    points = mutableListOf(current)
                }
                is PathSegmentF32.LineTo -> {
                    val destination = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val value = points ?: mutableListOf(current).also { points = it }
                    value += destination
                    current = destination
                    require(value.size <= MAX_POINTS_PER_CONTOUR_I32)
                }
                PathSegmentF32.Close -> {
                    closed = true
                    finish()
                }
                is PathSegmentF32.QuadTo,
                is PathSegmentF32.CubicTo,
                is PathSegmentF32.ArcTo,
                -> error("The independent W4d oracle accepts line fixtures only")
            }
        }
        finish()
        return contours
    }

    private fun containsFill(contours: List<ContourF64>, fillRule: FillRule, point: PointF64): Boolean {
        require(fillRule == FillRule.WINDING || fillRule == FillRule.EVEN_ODD)
        var windingI32 = 0
        var crossingCountI32 = 0
        contours.forEach { contour ->
            if (contour.points.size < 3) return@forEach
            contour.points.indices.forEach { indexI32 ->
                val first = contour.points[indexI32]
                val second = contour.points[(indexI32 + 1) % contour.points.size]
                if ((first.y <= point.y && second.y > point.y) || (second.y <= point.y && first.y > point.y)) {
                    val crossingX = first.x + (point.y - first.y) * (second.x - first.x) / (second.y - first.y)
                    if (crossingX > point.x) {
                        crossingCountI32 += 1
                        windingI32 += if (second.y > first.y) 1 else -1
                    }
                }
            }
        }
        return if (fillRule == FillRule.WINDING) windingI32 != 0 else crossingCountI32 % 2 != 0
    }

    private fun map(matrix: Matrix3x3F32, point: PointF64): PointF64 = PointF64(
        matrix.sx.toDouble() * point.x + matrix.tx.toDouble(),
        matrix.sy.toDouble() * point.y + matrix.ty.toDouble(),
    )

    private fun inverseMap(matrix: Matrix3x3F32, point: PointF64): PointF64 {
        require(matrix.sx != 0f && matrix.sy != 0f)
        return PointF64(
            (point.x - matrix.tx.toDouble()) / matrix.sx.toDouble(),
            (point.y - matrix.ty.toDouble()) / matrix.sy.toDouble(),
        )
    }

    private fun normalized(vector: PointF64): PointF64 {
        val lengthF64 = sqrt(vector.dot(vector))
        require(lengthF64 > 0.0)
        return vector * (1.0 / lengthF64)
    }

    private fun squaredDistance(first: PointF64, second: PointF64): Double =
        (first - second).let { it.dot(it) }

    private fun srcOver(source: LinearPremul, destination: LinearPremul): LinearPremul {
        val inverseSourceAlpha = 1f - source.alpha
        return LinearPremul(
            source.red + destination.red * inverseSourceAlpha,
            source.green + destination.green * inverseSourceAlpha,
            source.blue + destination.blue * inverseSourceAlpha,
            source.alpha + destination.alpha * inverseSourceAlpha,
        )
    }

    private data class PointF64(val x: Double, val y: Double) {
        operator fun plus(other: PointF64): PointF64 = PointF64(x + other.x, y + other.y)
        operator fun minus(other: PointF64): PointF64 = PointF64(x - other.x, y - other.y)
        operator fun times(valueF64: Double): PointF64 = PointF64(x * valueF64, y * valueF64)
        fun dot(other: PointF64): Double = x * other.x + y * other.y
        fun cross(other: PointF64): Double = x * other.y - y * other.x
    }

    private data class LineF64(val start: PointF64, val end: PointF64)
    private data class ContourF64(val points: List<PointF64>, val closed: Boolean)
    private data class RunF64(val points: List<PointF64>, val closed: Boolean)

    private data class LinearPremul(val red: Float, val green: Float, val blue: Float, val alpha: Float) {
        fun quantizedForAttachment(): LinearPremul = LinearPremul(
            red.toSrgbByte().toInt().srgbToLinear(),
            green.toSrgbByte().toInt().srgbToLinear(),
            blue.toSrgbByte().toInt().srgbToLinear(),
            alpha.toQuantizedUByte().toInt() / 255f,
        )

        companion object {
            val Transparent: LinearPremul = LinearPremul(0f, 0f, 0f, 0f)

            fun from(color: ColorARGB): LinearPremul {
                val alpha = color.alpha / 255f
                return LinearPremul(
                    color.red.srgbToLinear() * alpha,
                    color.green.srgbToLinear() * alpha,
                    color.blue.srgbToLinear() * alpha,
                    alpha,
                )
            }
        }
    }

    private fun Int.srgbToLinear(): Float {
        val encoded = this / 255f
        return if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun Float.toSrgbByte(): UByte {
        val linear = coerceIn(0f, 1f)
        val encoded = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (encoded * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()
    }

    private fun Float.toQuantizedUByte(): UByte =
        (coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()

    private const val CHANNELS_PER_PIXEL_I32 = 4
    private const val MAX_DRAWS_I32 = 512
    private const val MAX_CONTOURS_I32 = 64
    private const val MAX_POINTS_PER_CONTOUR_I32 = 4_096
    private const val MAX_DASH_RUNS_I32 = 8_192
    private const val EPSILON_F64 = 1e-9
}
