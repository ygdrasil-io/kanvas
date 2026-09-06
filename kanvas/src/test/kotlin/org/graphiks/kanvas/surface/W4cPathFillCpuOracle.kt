package org.graphiks.kanvas.surface

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Test-only reference for W4c's public path-fill pixel contract.
 *
 * This is intentionally independent from the W4c planner, renderer, payloads,
 * flattening, and prepared geometry.  It starts from the original path verbs,
 * maps them directly in F64, and evaluates fill crossings at pixel centres.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4cPathFillCpuOracle {
    internal data class Draw(
        val path: PathF32,
        val transform: Matrix3x3F32,
        val color: ColorARGB,
        val scissorI32: RectI32,
    )

    internal fun render(
        widthI32: Int,
        heightI32: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
        quantizeBetweenDraws: Boolean = true,
    ): UByteArray {
        require(widthI32 > 0 && heightI32 > 0)
        val pixels = Array(widthI32 * heightI32) { LinearPremul.Transparent }
        draws.forEach { draw ->
            if (draw.path.hasCurve()) {
                require(certifyCurveFixture(widthI32, heightI32, draw) is CurveFixtureCertificate.Certified) {
                    "Curve fixtures must be certified before they produce expected W4c pixels."
                }
            }
            val contours = lineContours(draw.path, draw.transform)
            val source = LinearPremul.from(draw.color)
            for (yI32 in 0 until heightI32) {
                for (xI32 in 0 until widthI32) {
                    if (
                        xI32 !in draw.scissorI32.left until draw.scissorI32.right ||
                        yI32 !in draw.scissorI32.top until draw.scissorI32.bottom
                    ) continue
                    if (!contains(contours, draw.path.fillRule, xI32 + 0.5, yI32 + 0.5)) continue

                    val pixelIndexI32 = yI32 * widthI32 + xI32
                    val composited = srcOver(source, pixels[pixelIndexI32])
                    pixels[pixelIndexI32] = if (quantizeBetweenDraws) {
                        composited.quantizedForAttachment()
                    } else {
                        composited
                    }
                }
            }
        }
        return UByteArray(widthI32 * heightI32 * CHANNELS_PER_PIXEL).also { bytes ->
            pixels.forEachIndexed { pixelIndexI32, color ->
                val offsetI32 = pixelIndexI32 * CHANNELS_PER_PIXEL
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

    /** Deliberate counterfactual used to prove attachment quantization is per draw. */
    internal fun renderWithFrameEndQuantization(
        widthI32: Int,
        heightI32: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray = render(
        widthI32 = widthI32,
        heightI32 = heightI32,
        draws = draws,
        format = format,
        quantizeBetweenDraws = false,
    )

    private fun lineContours(path: PathF32, transform: Matrix3x3F32): List<List<PointF64>> {
        val retained = mutableListOf<List<PointF64>>()
        var contour: MutableList<PointF64>? = null
        var sourceCurrent = PointF64(0.0, 0.0)
        var sourceContourStart = sourceCurrent
        var current = map(transform, sourceCurrent.x, sourceCurrent.y)
        var contourStart = current

        fun finishContour() {
            val value = contour ?: return
            contour = null
            val normalized = normalizeContour(value)
            if (normalized != null) retained += normalized
        }

        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    finishContour()
                    sourceCurrent = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    sourceContourStart = sourceCurrent
                    current = map(transform, sourceCurrent.x, sourceCurrent.y)
                    contourStart = current
                    contour = mutableListOf(current)
                }
                is PathSegmentF32.LineTo -> {
                    val sourceDestination = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val destination = map(transform, sourceDestination.x, sourceDestination.y)
                    val value = contour ?: mutableListOf(current).also {
                        contour = it
                        contourStart = current
                        sourceContourStart = sourceCurrent
                    }
                    value += destination
                    sourceCurrent = sourceDestination
                    current = destination
                }
                is PathSegmentF32.QuadTo -> {
                    val sourceDestination = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val destination = map(transform, sourceDestination.x, sourceDestination.y)
                    val value = contour ?: mutableListOf(current).also {
                        contour = it
                        contourStart = current
                        sourceContourStart = sourceCurrent
                    }
                    val control = map(transform, segment.control.x.toDouble(), segment.control.y.toDouble())
                    value += flattenQuad(current, control, destination)
                    sourceCurrent = sourceDestination
                    current = destination
                }
                is PathSegmentF32.CubicTo -> {
                    val sourceDestination = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val destination = map(transform, sourceDestination.x, sourceDestination.y)
                    val value = contour ?: mutableListOf(current).also {
                        contour = it
                        contourStart = current
                        sourceContourStart = sourceCurrent
                    }
                    val control1 = map(transform, segment.control1.x.toDouble(), segment.control1.y.toDouble())
                    val control2 = map(transform, segment.control2.x.toDouble(), segment.control2.y.toDouble())
                    value += flattenCubic(current, control1, control2, destination)
                    sourceCurrent = sourceDestination
                    current = destination
                }
                is PathSegmentF32.ArcTo -> {
                    val sourceDestination = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val destination = map(transform, sourceDestination.x, sourceDestination.y)
                    val value = contour ?: mutableListOf(current).also {
                        contour = it
                        contourStart = current
                        sourceContourStart = sourceCurrent
                    }
                    value += flattenArc(sourceCurrent, segment, transform)
                    sourceCurrent = sourceDestination
                    current = destination
                }
                is PathSegmentF32.Close -> {
                    val value = contour
                    if (value != null) {
                        if (value.last() != contourStart) value += contourStart
                        current = contourStart
                        sourceCurrent = sourceContourStart
                        finishContour()
                    }
                }
            }
        }
        finishContour()
        return retained
    }

    private fun normalizeContour(points: List<PointF64>): List<PointF64>? {
        val withoutConsecutiveDuplicates = buildList {
            points.forEach { point -> if (lastOrNull() != point) add(point) }
        }.toMutableList()
        if (withoutConsecutiveDuplicates.size > 1 && withoutConsecutiveDuplicates.first() == withoutConsecutiveDuplicates.last()) {
            withoutConsecutiveDuplicates.removeLast()
        }
        if (withoutConsecutiveDuplicates.distinct().size < 3) return null
        val first = withoutConsecutiveDuplicates.first()
        val second = withoutConsecutiveDuplicates.firstOrNull { it != first } ?: return null
        return withoutConsecutiveDuplicates.takeIf { points ->
            points.any { point ->
                val cross = (second.x - first.x) * (point.y - first.y) -
                    (second.y - first.y) * (point.x - first.x)
                cross != 0.0
            }
        }
    }

    private fun contains(
        contours: List<List<PointF64>>,
        fillRule: FillRule,
        pixelCenterX: Double,
        pixelCenterY: Double,
    ): Boolean {
        require(fillRule == FillRule.WINDING || fillRule == FillRule.EVEN_ODD)
        var windingI32 = 0
        var crossingsI32 = 0
        contours.forEach { contour ->
            contour.indices.forEach { indexI32 ->
                val first = contour[indexI32]
                val second = contour[(indexI32 + 1) % contour.size]
                if ((first.y <= pixelCenterY && second.y > pixelCenterY) ||
                    (second.y <= pixelCenterY && first.y > pixelCenterY)
                ) {
                    val crossingX = first.x + (pixelCenterY - first.y) * (second.x - first.x) / (second.y - first.y)
                    if (crossingX > pixelCenterX) {
                        crossingsI32 += 1
                        windingI32 += if (second.y > first.y) 1 else -1
                    }
                }
            }
        }
        return when (fillRule) {
            FillRule.WINDING -> windingI32 != 0
            FillRule.EVEN_ODD -> crossingsI32 % 2 != 0
            FillRule.INVERSE_WINDING,
            FillRule.INVERSE_EVEN_ODD
            -> error("Only finite W4c fill rules are accepted")
        }
    }

    private fun map(matrix: Matrix3x3F32, x: Double, y: Double): PointF64 {
        val mappedX = matrix.sx.toDouble() * x + matrix.kx.toDouble() * y + matrix.tx.toDouble()
        val mappedY = matrix.ky.toDouble() * x + matrix.sy.toDouble() * y + matrix.ty.toDouble()
        val homogeneousW = matrix.persp0.toDouble() * x + matrix.persp1.toDouble() * y + matrix.persp2.toDouble()
        require(mappedX.isFinite() && mappedY.isFinite() && homogeneousW.isFinite() && homogeneousW != 0.0)
        return PointF64(mappedX / homogeneousW, mappedY / homogeneousW)
    }

    internal sealed interface CurveFixtureCertificate {
        data class Certified(val intervalCountI32: Int) : CurveFixtureCertificate

        data class Uncertified(val reason: String) : CurveFixtureCertificate
    }

    /**
     * Conservatively proves that an independently flattened curve fixture cannot
     * change a pixel-centre crossing or tie when W4c permits 0.25 px sagitta.
     */
    internal fun certifyCurveFixture(
        widthI32: Int,
        heightI32: Int,
        draw: Draw,
    ): CurveFixtureCertificate {
        if (widthI32 <= 0 || heightI32 <= 0) {
            return CurveFixtureCertificate.Uncertified("non-positive target extent")
        }
        if (!draw.transform.isScaleTranslate()) {
            return CurveFixtureCertificate.Uncertified("curve certificate requires an axis-aligned transform")
        }
        val intervals = curveIntervals(draw.path, draw.transform)
        if (intervals.size > MAX_CERTIFICATE_INTERVALS) {
            return CurveFixtureCertificate.Uncertified("interval subdivision exceeded the test-only certificate budget")
        }
        for (yI32 in 0 until heightI32) {
            for (xI32 in 0 until widthI32) {
                val center = PointF64(xI32 + 0.5, yI32 + 0.5)
                intervals.forEach { interval ->
                    val margin = W4C_MAXIMUM_SAGITTA_ERROR + interval.f32RoundTripBoundF64 + interval.intervalBoundF64
                    if (distanceToBounds(center, interval.bounds) <= margin) {
                        return CurveFixtureCertificate.Uncertified(
                            "pixel centre ($xI32,$yI32) is too close to a curve enclosure",
                        )
                    }
                    if (center.y in interval.bounds.minY - margin..interval.bounds.maxY + margin) {
                        if (!interval.monotonicY) {
                            return CurveFixtureCertificate.Uncertified(
                                "pixel centre ($xI32,$yI32) has a non-unique curve crossing",
                            )
                        }
                        if (abs(interval.start.y - center.y) <= margin || abs(interval.end.y - center.y) <= margin) {
                            return CurveFixtureCertificate.Uncertified(
                                "pixel centre ($xI32,$yI32) is tied to a curve interval endpoint",
                            )
                        }
                    }
                }
            }
        }
        return CurveFixtureCertificate.Certified(intervals.size)
    }

    private fun curveIntervals(path: PathF32, transform: Matrix3x3F32): List<CurveInterval> {
        val intervals = mutableListOf<CurveInterval>()
        var sourceCurrent = PointF64(0.0, 0.0)
        var sourceContourStart = sourceCurrent
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    sourceCurrent = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    sourceContourStart = sourceCurrent
                }
                is PathSegmentF32.LineTo -> {
                    sourceCurrent = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                }
                is PathSegmentF32.QuadTo -> {
                    val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    subdivideQuadInterval(
                        start = map(transform, sourceCurrent.x, sourceCurrent.y),
                        control = map(transform, segment.control.x.toDouble(), segment.control.y.toDouble()),
                        end = map(transform, end.x, end.y),
                        depthI32 = 0,
                        destination = intervals,
                    )
                    sourceCurrent = end
                }
                is PathSegmentF32.CubicTo -> {
                    val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    subdivideCubicInterval(
                        start = map(transform, sourceCurrent.x, sourceCurrent.y),
                        control1 = map(transform, segment.control1.x.toDouble(), segment.control1.y.toDouble()),
                        control2 = map(transform, segment.control2.x.toDouble(), segment.control2.y.toDouble()),
                        end = map(transform, end.x, end.y),
                        depthI32 = 0,
                        destination = intervals,
                    )
                    sourceCurrent = end
                }
                is PathSegmentF32.ArcTo -> {
                    val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    svgArc(sourceCurrent, segment, end)?.let { arc ->
                        subdivideArcInterval(arc, transform, 0.0, 1.0, 0, intervals)
                    }
                    sourceCurrent = end
                }
                is PathSegmentF32.Close -> sourceCurrent = sourceContourStart
            }
        }
        return intervals
    }

    private fun subdivideQuadInterval(
        start: PointF64,
        control: PointF64,
        end: PointF64,
        depthI32: Int,
        destination: MutableList<CurveInterval>,
    ) {
        val bounds = BoundsF64.of(start, control, end).outward()
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && bounds.maxDimensionF64 > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val startControl = midpoint(start, control)
            val controlEnd = midpoint(control, end)
            val middle = midpoint(startControl, controlEnd)
            subdivideQuadInterval(start, startControl, middle, depthI32 + 1, destination)
            subdivideQuadInterval(middle, controlEnd, end, depthI32 + 1, destination)
            return
        }
        destination += CurveInterval(
            bounds = bounds,
            start = start,
            end = end,
            monotonicY = monotonic(start.y, control.y, end.y),
        )
    }

    private fun subdivideCubicInterval(
        start: PointF64,
        control1: PointF64,
        control2: PointF64,
        end: PointF64,
        depthI32: Int,
        destination: MutableList<CurveInterval>,
    ) {
        val bounds = BoundsF64.of(start, control1, control2, end).outward()
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && bounds.maxDimensionF64 > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val startControl1 = midpoint(start, control1)
            val control1Control2 = midpoint(control1, control2)
            val control2End = midpoint(control2, end)
            val leftControl2 = midpoint(startControl1, control1Control2)
            val rightControl1 = midpoint(control1Control2, control2End)
            val middle = midpoint(leftControl2, rightControl1)
            subdivideCubicInterval(start, startControl1, leftControl2, middle, depthI32 + 1, destination)
            subdivideCubicInterval(middle, rightControl1, control2End, end, depthI32 + 1, destination)
            return
        }
        destination += CurveInterval(
            bounds = bounds,
            start = start,
            end = end,
            monotonicY = monotonic(start.y, control1.y, control2.y, end.y),
        )
    }

    private fun subdivideArcInterval(
        arc: SvgArcF64,
        transform: Matrix3x3F32,
        fromT: Double,
        toT: Double,
        depthI32: Int,
        destination: MutableList<CurveInterval>,
    ) {
        val bounds = arc.bounds(transform, fromT, toT)
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && bounds.maxDimensionF64 > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val middle = (fromT + toT) * 0.5
            subdivideArcInterval(arc, transform, fromT, middle, depthI32 + 1, destination)
            subdivideArcInterval(arc, transform, middle, toT, depthI32 + 1, destination)
            return
        }
        val start = arc.pointAt(fromT).let { point -> map(transform, point.x, point.y) }
        val end = arc.pointAt(toT).let { point -> map(transform, point.x, point.y) }
        destination += CurveInterval(
            bounds = bounds,
            start = start,
            end = end,
            monotonicY = arc.isMonotonicY(transform, fromT, toT),
        )
    }

    private fun distanceToBounds(point: PointF64, bounds: BoundsF64): Double {
        val deltaX = when {
            point.x < bounds.minX -> bounds.minX - point.x
            point.x > bounds.maxX -> point.x - bounds.maxX
            else -> 0.0
        }
        val deltaY = when {
            point.y < bounds.minY -> bounds.minY - point.y
            point.y > bounds.maxY -> point.y - bounds.maxY
            else -> 0.0
        }
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun monotonic(vararg values: Double): Boolean {
        var nonDecreasing = true
        var nonIncreasing = true
        for (indexI32 in 1 until values.size) {
            nonDecreasing = nonDecreasing && values[indexI32 - 1] <= values[indexI32]
            nonIncreasing = nonIncreasing && values[indexI32 - 1] >= values[indexI32]
        }
        return nonDecreasing || nonIncreasing
    }

    private fun PathF32.hasCurve(): Boolean = any { segment ->
        segment is PathSegmentF32.QuadTo ||
            segment is PathSegmentF32.CubicTo ||
            segment is PathSegmentF32.ArcTo
    }

    private data class CurveInterval(
        val bounds: BoundsF64,
        val start: PointF64,
        val end: PointF64,
        val monotonicY: Boolean,
    ) {
        private val magnitudeF64: Double = maxOf(
            abs(bounds.minX),
            abs(bounds.minY),
            abs(bounds.maxX),
            abs(bounds.maxY),
            abs(start.x),
            abs(start.y),
            abs(end.x),
            abs(end.y),
            1.0,
        )

        /** A deliberately loose F32 round-trip allowance for test fixtures. */
        val f32RoundTripBoundF64: Double = magnitudeF64 * F32_RELATIVE_ERROR_BOUND

        /** Bounds arithmetic is F64; retain a conservative enclosure allowance. */
        val intervalBoundF64: Double = magnitudeF64 * F64_RELATIVE_ERROR_BOUND
    }

    private data class BoundsF64(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    ) {
        val maxDimensionF64: Double get() = maxOf(maxX - minX, maxY - minY)

        fun outward(): BoundsF64 {
            val allowance = maxOf(
                abs(minX),
                abs(minY),
                abs(maxX),
                abs(maxY),
                1.0,
            ) * F64_RELATIVE_ERROR_BOUND
            return BoundsF64(
                minX = minX - allowance,
                minY = minY - allowance,
                maxX = maxX + allowance,
                maxY = maxY + allowance,
            )
        }

        companion object {
            fun of(vararg points: PointF64): BoundsF64 = BoundsF64(
                minX = points.minOf(PointF64::x),
                minY = points.minOf(PointF64::y),
                maxX = points.maxOf(PointF64::x),
                maxY = points.maxOf(PointF64::y),
            )
        }
    }

    /**
     * Independent SVG endpoint-arc conversion.  W4c's production preparation
     * is intentionally never consulted by this test oracle.
     */
    private data class SvgArcF64(
        val center: PointF64,
        val radiusX: Double,
        val radiusY: Double,
        val cosineAxis: Double,
        val sineAxis: Double,
        val startAngle: Double,
        val sweepAngle: Double,
    ) {
        fun pointAt(t: Double): PointF64 {
            val angle = startAngle + sweepAngle * t
            val localX = radiusX * cos(angle)
            val localY = radiusY * sin(angle)
            return PointF64(
                center.x + cosineAxis * localX - sineAxis * localY,
                center.y + sineAxis * localX + cosineAxis * localY,
            )
        }

        fun bounds(matrix: Matrix3x3F32, fromT: Double, toT: Double): BoundsF64 {
            val angles = mutableListOf(
                startAngle + sweepAngle * fromT,
                startAngle + sweepAngle * toT,
            )
            // x(theta) = cx + A cos(theta) + B sin(theta)
            addExtremaAngles(
                angles = angles,
                baseAngle = atan2(-radiusY * sineAxis, radiusX * cosineAxis),
                fromT = fromT,
                toT = toT,
            )
            // y(theta) = cy + A cos(theta) + B sin(theta)
            addExtremaAngles(
                angles = angles,
                baseAngle = atan2(radiusY * cosineAxis, radiusX * sineAxis),
                fromT = fromT,
                toT = toT,
            )
            return BoundsF64.of(*angles.map { angle ->
                val t = (angle - startAngle) / sweepAngle
                pointAt(t).let { point -> map(matrix, point.x, point.y) }
            }.toTypedArray()).outward()
        }

        fun isMonotonicY(matrix: Matrix3x3F32, fromT: Double, toT: Double): Boolean {
            if (matrix.sy == 0f) return false
            return !hasInteriorExtremum(
                baseAngle = atan2(radiusY * cosineAxis, radiusX * sineAxis),
                fromT = fromT,
                toT = toT,
            )
        }

        private fun addExtremaAngles(
            angles: MutableList<Double>,
            baseAngle: Double,
            fromT: Double,
            toT: Double,
        ) {
            val fromAngle = startAngle + sweepAngle * fromT
            val toAngle = startAngle + sweepAngle * toT
            val lower = minOf(fromAngle, toAngle)
            val upper = maxOf(fromAngle, toAngle)
            var multiple = floor((lower - baseAngle) / PI).toInt() - 1
            val last = floor((upper - baseAngle) / PI).toInt() + 1
            while (multiple <= last) {
                val angle = baseAngle + multiple * PI
                val t = (angle - startAngle) / sweepAngle
                if (t > fromT && t < toT) angles += angle
                multiple += 1
            }
        }

        private fun hasInteriorExtremum(baseAngle: Double, fromT: Double, toT: Double): Boolean {
            val fromAngle = startAngle + sweepAngle * fromT
            val toAngle = startAngle + sweepAngle * toT
            val lower = minOf(fromAngle, toAngle)
            val upper = maxOf(fromAngle, toAngle)
            var multiple = floor((lower - baseAngle) / PI).toInt() - 1
            val last = floor((upper - baseAngle) / PI).toInt() + 1
            while (multiple <= last) {
                val angle = baseAngle + multiple * PI
                val t = (angle - startAngle) / sweepAngle
                if (t > fromT && t < toT) return true
                multiple += 1
            }
            return false
        }
    }

    private fun svgArc(
        start: PointF64,
        segment: PathSegmentF32.ArcTo,
        end: PointF64,
    ): SvgArcF64? {
        var radiusX = abs(segment.radius.x.toDouble())
        var radiusY = abs(segment.radius.y.toDouble())
        if (
            start == end ||
            radiusX == 0.0 ||
            radiusY == 0.0 ||
            !radiusX.isFinite() ||
            !radiusY.isFinite() ||
            !segment.xAxisRotation.isFinite()
        ) return null

        val axisRadians = segment.xAxisRotation.toDouble() * PI / 180.0
        val cosineAxis = cos(axisRadians)
        val sineAxis = sin(axisRadians)
        val midpointX = (start.x - end.x) * 0.5
        val midpointY = (start.y - end.y) * 0.5
        val primeX = cosineAxis * midpointX + sineAxis * midpointY
        val primeY = -sineAxis * midpointX + cosineAxis * midpointY
        val lambda = primeX * primeX / (radiusX * radiusX) + primeY * primeY / (radiusY * radiusY)
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            radiusX *= scale
            radiusY *= scale
        }

        val radiusXSquared = radiusX * radiusX
        val radiusYSquared = radiusY * radiusY
        val primeXSquared = primeX * primeX
        val primeYSquared = primeY * primeY
        val denominator = radiusXSquared * primeYSquared + radiusYSquared * primeXSquared
        if (denominator == 0.0 || !denominator.isFinite()) return null
        val numerator = (radiusXSquared * radiusYSquared) -
            (radiusXSquared * primeYSquared) -
            (radiusYSquared * primeXSquared)
        val sign = if (segment.largeArc == segment.sweep) -1.0 else 1.0
        val coefficient = sign * sqrt((numerator / denominator).coerceAtLeast(0.0))
        val centerPrimeX = coefficient * (radiusX * primeY / radiusY)
        val centerPrimeY = coefficient * (-radiusY * primeX / radiusX)
        val center = PointF64(
            cosineAxis * centerPrimeX - sineAxis * centerPrimeY + (start.x + end.x) * 0.5,
            sineAxis * centerPrimeX + cosineAxis * centerPrimeY + (start.y + end.y) * 0.5,
        )
        val unitStartX = (primeX - centerPrimeX) / radiusX
        val unitStartY = (primeY - centerPrimeY) / radiusY
        val unitEndX = (-primeX - centerPrimeX) / radiusX
        val unitEndY = (-primeY - centerPrimeY) / radiusY
        val startAngle = atan2(unitStartY, unitStartX)
        var sweepAngle = atan2(
            unitStartX * unitEndY - unitStartY * unitEndX,
            unitStartX * unitEndX + unitStartY * unitEndY,
        )
        if (!segment.sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
        if (segment.sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI
        return SvgArcF64(
            center = center,
            radiusX = radiusX,
            radiusY = radiusY,
            cosineAxis = cosineAxis,
            sineAxis = sineAxis,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
        )
    }

    private fun flattenQuad(
        start: PointF64,
        control: PointF64,
        end: PointF64,
    ): List<PointF64> = buildList {
        fun append(startPoint: PointF64, controlPoint: PointF64, endPoint: PointF64, depthI32: Int) {
            if (depthI32 == ORACLE_MAX_SUBDIVISION_DEPTH || distanceToChord(controlPoint, startPoint, endPoint) <= ORACLE_CURVE_FLATTENING_ERROR) {
                add(endPoint)
                return
            }
            val startControl = midpoint(startPoint, controlPoint)
            val controlEnd = midpoint(controlPoint, endPoint)
            val middle = midpoint(startControl, controlEnd)
            append(startPoint, startControl, middle, depthI32 + 1)
            append(middle, controlEnd, endPoint, depthI32 + 1)
        }
        append(start, control, end, 0)
    }

    private fun flattenCubic(
        start: PointF64,
        control1: PointF64,
        control2: PointF64,
        end: PointF64,
    ): List<PointF64> = buildList {
        fun append(
            startPoint: PointF64,
            control1Point: PointF64,
            control2Point: PointF64,
            endPoint: PointF64,
            depthI32: Int,
        ) {
            if (
                depthI32 == ORACLE_MAX_SUBDIVISION_DEPTH ||
                maxOf(
                    distanceToChord(control1Point, startPoint, endPoint),
                    distanceToChord(control2Point, startPoint, endPoint),
                ) <= ORACLE_CURVE_FLATTENING_ERROR
            ) {
                add(endPoint)
                return
            }
            val startControl1 = midpoint(startPoint, control1Point)
            val control1Control2 = midpoint(control1Point, control2Point)
            val control2End = midpoint(control2Point, endPoint)
            val leftControl2 = midpoint(startControl1, control1Control2)
            val rightControl1 = midpoint(control1Control2, control2End)
            val middle = midpoint(leftControl2, rightControl1)
            append(startPoint, startControl1, leftControl2, middle, depthI32 + 1)
            append(middle, rightControl1, control2End, endPoint, depthI32 + 1)
        }
        append(start, control1, control2, end, 0)
    }

    private fun flattenArc(
        start: PointF64,
        segment: PathSegmentF32.ArcTo,
        transform: Matrix3x3F32,
    ): List<PointF64> {
        val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
        val arc = svgArc(start, segment, end) ?: return if (start == end) emptyList() else listOf(map(transform, end.x, end.y))
        return buildList {
            fun append(
                fromT: Double,
                from: PointF64,
                toT: Double,
                to: PointF64,
                depthI32: Int,
            ) {
                val middleT = (fromT + toT) * 0.5
                val middle = arc.pointAt(middleT).let { point -> map(transform, point.x, point.y) }
                if (
                    depthI32 == ORACLE_MAX_SUBDIVISION_DEPTH ||
                    distanceToChord(middle, from, to) <= ORACLE_CURVE_FLATTENING_ERROR
                ) {
                    add(to)
                    return
                }
                append(fromT, from, middleT, middle, depthI32 + 1)
                append(middleT, middle, toT, to, depthI32 + 1)
            }
            val startDevice = map(transform, start.x, start.y)
            val endDevice = map(transform, end.x, end.y)
            append(0.0, startDevice, 1.0, endDevice, 0)
        }
    }

    private fun distanceToChord(point: PointF64, start: PointF64, end: PointF64): Double {
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        val length = sqrt(deltaX * deltaX + deltaY * deltaY)
        return if (length == 0.0) {
            sqrt((point.x - start.x) * (point.x - start.x) + (point.y - start.y) * (point.y - start.y))
        } else {
            kotlin.math.abs(deltaX * (start.y - point.y) - (start.x - point.x) * deltaY) / length
        }
    }

    private fun midpoint(first: PointF64, second: PointF64): PointF64 =
        PointF64((first.x + second.x) * 0.5, (first.y + second.y) * 0.5)

    private fun srcOver(source: LinearPremul, destination: LinearPremul): LinearPremul {
        val inverseSourceAlpha = 1f - source.alpha
        return LinearPremul(
            red = source.red + destination.red * inverseSourceAlpha,
            green = source.green + destination.green * inverseSourceAlpha,
            blue = source.blue + destination.blue * inverseSourceAlpha,
            alpha = source.alpha + destination.alpha * inverseSourceAlpha,
        )
    }

    private data class PointF64(val x: Double, val y: Double)

    private data class LinearPremul(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        fun quantizedForAttachment(): LinearPremul = LinearPremul(
            red = red.toSrgbByte().toInt().srgbToLinear(),
            green = green.toSrgbByte().toInt().srgbToLinear(),
            blue = blue.toSrgbByte().toInt().srgbToLinear(),
            alpha = alpha.toQuantizedUByte().toInt() / 255f,
        )

        companion object {
            val Transparent: LinearPremul = LinearPremul(0f, 0f, 0f, 0f)

            fun from(color: ColorARGB): LinearPremul {
                val alpha = color.alpha / 255f
                return LinearPremul(
                    red = color.red.srgbToLinear() * alpha,
                    green = color.green.srgbToLinear() * alpha,
                    blue = color.blue.srgbToLinear() * alpha,
                    alpha = alpha,
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

    private const val CHANNELS_PER_PIXEL = 4
    private const val W4C_MAXIMUM_SAGITTA_ERROR = 0.25
    private const val ORACLE_CURVE_FLATTENING_ERROR = 1.0 / 8_192.0
    private const val CERTIFICATE_INTERVAL_MAXIMUM_SPAN = 1.0 / 128.0
    private const val ORACLE_MAX_SUBDIVISION_DEPTH = 18
    private const val MAX_CERTIFICATE_INTERVALS = 65_536
    private const val F32_RELATIVE_ERROR_BOUND = 1.0 / 1_000_000.0
    private const val F64_RELATIVE_ERROR_BOUND = 1.0 / 1_000_000_000_000.0
}
