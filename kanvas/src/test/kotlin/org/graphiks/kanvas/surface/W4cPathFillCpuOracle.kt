package org.graphiks.kanvas.surface

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
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
        return certifyDirectedCurveFixture(widthI32, heightI32, draw)
    }

    private fun PathF32.hasCurve(): Boolean = any { segment ->
        segment is PathSegmentF32.QuadTo ||
            segment is PathSegmentF32.CubicTo ||
            segment is PathSegmentF32.ArcTo
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

    /**
     * A directed F64 interval for the curve-fixture certificate.  Every basic
     * operation evaluates the endpoint expression then takes the predecessor
     * or successor representable F64.  Invalid or overflowing expressions are
     * deliberately not approximated: their fixture is Uncertified.
     */
    private class DirectedF64 private constructor(
        val lowerF64: Double,
        val upperF64: Double,
    ) {
        fun plus(other: DirectedF64): DirectedF64? = of(
            down(lowerF64 + other.lowerF64) ?: return null,
            up(upperF64 + other.upperF64) ?: return null,
        )

        fun minus(other: DirectedF64): DirectedF64? = of(
            down(lowerF64 - other.upperF64) ?: return null,
            up(upperF64 - other.lowerF64) ?: return null,
        )

        fun times(other: DirectedF64): DirectedF64? {
            val products = doubleArrayOf(
                lowerF64 * other.lowerF64,
                lowerF64 * other.upperF64,
                upperF64 * other.lowerF64,
                upperF64 * other.upperF64,
            )
            if (products.any { !it.isFinite() }) return null
            return of(down(products.min()) ?: return null, up(products.max()) ?: return null)
        }

        fun dividedBy(other: DirectedF64): DirectedF64? {
            if (other.lowerF64 <= 0.0 && other.upperF64 >= 0.0) return null
            val quotients = doubleArrayOf(
                lowerF64 / other.lowerF64,
                lowerF64 / other.upperF64,
                upperF64 / other.lowerF64,
                upperF64 / other.upperF64,
            )
            if (quotients.any { !it.isFinite() }) return null
            return of(down(quotients.min()) ?: return null, up(quotients.max()) ?: return null)
        }

        fun negated(): DirectedF64? = of(
            down(-upperF64) ?: return null,
            up(-lowerF64) ?: return null,
        )

        fun squared(): DirectedF64? {
            val lower = if (lowerF64 <= 0.0 && upperF64 >= 0.0) {
                0.0
            } else {
                minOf(lowerF64 * lowerF64, upperF64 * upperF64)
            }
            val upper = maxOf(lowerF64 * lowerF64, upperF64 * upperF64)
            if (!lower.isFinite() || !upper.isFinite()) return null
            return of(
                if (lower == 0.0) 0.0 else down(lower) ?: return null,
                up(upper) ?: return null,
            )
        }

        /** SVG's centre coefficient is sqrt(max(0, numerator / denominator)). */
        fun sqrtAfterSvgClamp(): DirectedF64? {
            val lowerRoot = StrictMath.sqrt(maxOf(0.0, lowerF64))
            val upperRoot = StrictMath.sqrt(maxOf(0.0, upperF64))
            if (!lowerRoot.isFinite() || !upperRoot.isFinite()) return null
            return of(
                if (lowerRoot == 0.0) 0.0 else downTwice(lowerRoot) ?: return null,
                upTwice(upperRoot) ?: return null,
            )
        }

        fun spanUpperF64(): Double? = exact(upperF64)
            ?.minus(exact(lowerF64) ?: return null)
            ?.upperF64

        fun overlaps(other: DirectedF64): Boolean = lowerF64 <= other.upperF64 && upperF64 >= other.lowerF64

        fun lowerDistanceTo(value: Double): Double? = lowerDistanceF64(value, lowerF64, upperF64)

        companion object {
            fun exact(value: Double): DirectedF64? = of(value, value)

            fun of(lowerF64: Double, upperF64: Double): DirectedF64? =
                if (lowerF64.isFinite() && upperF64.isFinite() && lowerF64 <= upperF64) {
                    DirectedF64(lowerF64, upperF64)
                } else {
                    null
                }
        }
    }

    private data class DirectedPointF64(val x: DirectedF64, val y: DirectedF64) {
        fun midpoint(other: DirectedPointF64): DirectedPointF64? = DirectedPointF64(
            x = x.plus(other.x)?.times(DIRECTED_HALF) ?: return null,
            y = y.plus(other.y)?.times(DIRECTED_HALF) ?: return null,
        )

        fun squaredDistanceLowerF64(x: Double, y: Double): Double? =
            DirectedBoundsF64.of(this).squaredDistanceLowerF64(x, y)
    }

    private data class DirectedBoundsF64(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    ) {
        fun maximumSpanUpperF64(): Double? {
            val width = DirectedF64.exact(maxX)?.minus(DirectedF64.exact(minX) ?: return null)?.upperF64 ?: return null
            val height = DirectedF64.exact(maxY)?.minus(DirectedF64.exact(minY) ?: return null)?.upperF64 ?: return null
            return maxOf(width, height)
        }

        fun squaredDistanceLowerF64(x: Double, y: Double): Double? {
            val deltaX = lowerDistanceF64(x, minX, maxX) ?: return null
            val deltaY = lowerDistanceF64(y, minY, maxY) ?: return null
            val deltaXSquared = DirectedF64.exact(deltaX)?.squared()?.lowerF64 ?: return null
            val deltaYSquared = DirectedF64.exact(deltaY)?.squared()?.lowerF64 ?: return null
            return maxOf(
                0.0,
                DirectedF64.exact(deltaXSquared)
                    ?.plus(DirectedF64.exact(deltaYSquared) ?: return null)
                    ?.lowerF64
                    ?: return null,
            )
        }

        fun verticalDistanceLowerF64(y: Double): Double? = lowerDistanceF64(y, minY, maxY)

        /** A formal F32 round-trip bound derived from the enclosing Float ULP. */
        fun f32RoundTripUpperF64(): Double? {
            val maximumMagnitude = maxOf(abs(minX), abs(minY), abs(maxX), abs(maxY))
            val upperFloat = floatCeiling(maximumMagnitude) ?: return null
            return up(java.lang.Math.ulp(upperFloat).toDouble())
        }

        companion object {
            fun of(vararg points: DirectedPointF64): DirectedBoundsF64 = DirectedBoundsF64(
                minX = points.minOf { it.x.lowerF64 },
                minY = points.minOf { it.y.lowerF64 },
                maxX = points.maxOf { it.x.upperF64 },
                maxY = points.maxOf { it.y.upperF64 },
            )
        }
    }

    private data class DirectedCurveInterval(
        val bounds: DirectedBoundsF64,
        val start: DirectedPointF64,
        val end: DirectedPointF64,
        val monotonicY: Boolean,
    ) {
        fun marginUpperF64(): Double? = DirectedF64.exact(W4C_MAXIMUM_SAGITTA_ERROR)
            ?.plus(DirectedF64.exact(bounds.f32RoundTripUpperF64() ?: return null) ?: return null)
            ?.upperF64

        fun marginSquaredUpperF64(marginUpperF64: Double): Double? =
            DirectedF64.exact(marginUpperF64)?.squared()?.upperF64
    }

    private data class DirectedScaleTranslateF64(
        val sx: DirectedF64,
        val sy: DirectedF64,
        val tx: DirectedF64,
        val ty: DirectedF64,
    ) {
        fun map(point: DirectedPointF64?): DirectedPointF64? {
            point ?: return null
            return DirectedPointF64(
                x = sx.times(point.x)?.plus(tx) ?: return null,
                y = sy.times(point.y)?.plus(ty) ?: return null,
            )
        }

        companion object {
            fun of(matrix: Matrix3x3F32): DirectedScaleTranslateF64? {
                if (matrix.kx != 0f || matrix.ky != 0f || matrix.persp0 != 0f || matrix.persp1 != 0f || matrix.persp2 != 1f) {
                    return null
                }
                return DirectedScaleTranslateF64(
                    sx = DirectedF64.exact(matrix.sx.toDouble()) ?: return null,
                    sy = DirectedF64.exact(matrix.sy.toDouble()) ?: return null,
                    tx = DirectedF64.exact(matrix.tx.toDouble()) ?: return null,
                    ty = DirectedF64.exact(matrix.ty.toDouble()) ?: return null,
                )
            }
        }
    }

    private fun PointF64.toDirectedPointF64(): DirectedPointF64? = DirectedPointF64(
        x = DirectedF64.exact(x) ?: return null,
        y = DirectedF64.exact(y) ?: return null,
    )

    private fun lowerDistanceF64(value: Double, lowerF64: Double, upperF64: Double): Double? {
        val valueInterval = DirectedF64.exact(value) ?: return null
        val distance = when {
            value < lowerF64 -> DirectedF64.exact(lowerF64)?.minus(valueInterval)?.lowerF64
            value > upperF64 -> valueInterval.minus(DirectedF64.exact(upperF64) ?: return null)?.lowerF64
            else -> 0.0
        } ?: return null
        return maxOf(0.0, distance)
    }

    private fun floatCeiling(value: Double): Float? {
        if (!value.isFinite() || value < 0.0) return null
        val rounded = value.toFloat()
        if (!rounded.isFinite()) return null
        val ceiling = if (rounded.toDouble() < value) java.lang.Math.nextUp(rounded) else rounded
        return ceiling.takeIf { it.isFinite() }
    }

    private fun down(value: Double): Double? = java.lang.Math.nextDown(value).takeIf { it.isFinite() }

    private fun up(value: Double): Double? = java.lang.Math.nextUp(value).takeIf { it.isFinite() }

    private fun downTwice(value: Double): Double? = down(down(value) ?: return null)

    private fun upTwice(value: Double): Double? = up(up(value) ?: return null)

    private fun certifyDirectedCurveFixture(
        widthI32: Int,
        heightI32: Int,
        draw: Draw,
    ): CurveFixtureCertificate {
        val transform = DirectedScaleTranslateF64.of(draw.transform)
            ?: return CurveFixtureCertificate.Uncertified("curve certificate received a non-finite affine transform")
        val intervals = when (val result = directedCurveIntervals(draw.path, transform)) {
            is DirectedCurveIntervalsResult.Ready -> result.values
            is DirectedCurveIntervalsResult.Uncertified -> return CurveFixtureCertificate.Uncertified(result.reason)
        }
        for (yI32 in 0 until heightI32) {
            for (xI32 in 0 until widthI32) {
                if (
                    xI32 !in draw.scissorI32.left until draw.scissorI32.right ||
                    yI32 !in draw.scissorI32.top until draw.scissorI32.bottom
                ) continue
                val centerX = xI32 + 0.5
                val centerY = yI32 + 0.5
                for (interval in intervals) {
                    val marginUpperF64 = interval.marginUpperF64()
                        ?: return CurveFixtureCertificate.Uncertified("curve enclosure has no finite F32 round-trip bound")
                    val marginSquaredUpperF64 = interval.marginSquaredUpperF64(marginUpperF64)
                        ?: return CurveFixtureCertificate.Uncertified("curve enclosure has no finite squared margin")
                    val squaredDistanceLowerF64 = interval.bounds.squaredDistanceLowerF64(centerX, centerY)
                        ?: return CurveFixtureCertificate.Uncertified("pixel-centre distance enclosure is non-finite")
                    if (squaredDistanceLowerF64 <= marginSquaredUpperF64) {
                        return CurveFixtureCertificate.Uncertified(
                            "pixel centre ($xI32,$yI32) is too close to a curve enclosure",
                        )
                    }
                    val verticalDistanceLowerF64 = interval.bounds.verticalDistanceLowerF64(centerY)
                        ?: return CurveFixtureCertificate.Uncertified("pixel-centre vertical distance enclosure is non-finite")
                    if (verticalDistanceLowerF64 <= marginUpperF64) {
                        if (!interval.monotonicY) {
                            return CurveFixtureCertificate.Uncertified(
                                "pixel centre ($xI32,$yI32) has a non-unique curve crossing",
                            )
                        }
                        val startDistanceSquaredLowerF64 = interval.start.squaredDistanceLowerF64(centerX, centerY)
                            ?: return CurveFixtureCertificate.Uncertified("curve start enclosure is non-finite")
                        val endDistanceSquaredLowerF64 = interval.end.squaredDistanceLowerF64(centerX, centerY)
                            ?: return CurveFixtureCertificate.Uncertified("curve end enclosure is non-finite")
                        if (
                            startDistanceSquaredLowerF64 <= marginSquaredUpperF64 ||
                            endDistanceSquaredLowerF64 <= marginSquaredUpperF64
                        ) {
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

    private sealed interface DirectedCurveIntervalsResult {
        data class Ready(val values: List<DirectedCurveInterval>) : DirectedCurveIntervalsResult

        data class Uncertified(val reason: String) : DirectedCurveIntervalsResult
    }

    private fun directedCurveIntervals(
        path: PathF32,
        transform: DirectedScaleTranslateF64,
    ): DirectedCurveIntervalsResult {
        val intervals = mutableListOf<DirectedCurveInterval>()
        var sourceCurrent = PointF64(0.0, 0.0)
        var sourceContourStart = sourceCurrent
        var failure: String? = null
        path.forEach { segment ->
            if (failure != null) return@forEach
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
                    val start = transform.map(sourceCurrent.toDirectedPointF64())
                    val control = transform.map(
                        PointF64(segment.control.x.toDouble(), segment.control.y.toDouble()).toDirectedPointF64(),
                    )
                    val destination = transform.map(end.toDirectedPointF64())
                    failure = if (start == null || control == null || destination == null) {
                        "non-finite quadratic curve input"
                    } else {
                        subdivideDirectedQuad(start, control, destination, 0, intervals)
                    }
                    sourceCurrent = end
                }
                is PathSegmentF32.CubicTo -> {
                    val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    val start = transform.map(sourceCurrent.toDirectedPointF64())
                    val control1 = transform.map(
                        PointF64(segment.control1.x.toDouble(), segment.control1.y.toDouble()).toDirectedPointF64(),
                    )
                    val control2 = transform.map(
                        PointF64(segment.control2.x.toDouble(), segment.control2.y.toDouble()).toDirectedPointF64(),
                    )
                    val destination = transform.map(end.toDirectedPointF64())
                    failure = if (start == null || control1 == null || control2 == null || destination == null) {
                        "non-finite cubic curve input"
                    } else {
                        subdivideDirectedCubic(start, control1, control2, destination, 0, intervals)
                    }
                    sourceCurrent = end
                }
                is PathSegmentF32.ArcTo -> {
                    val end = PointF64(segment.point.x.toDouble(), segment.point.y.toDouble())
                    when (val arc = directedSvgArc(sourceCurrent, segment, end)) {
                        DirectedSvgArcResult.Line -> Unit
                        is DirectedSvgArcResult.Ready -> {
                            failure = subdivideDirectedArc(arc.value, transform, 0.0, 1.0, 0, intervals)
                        }
                        is DirectedSvgArcResult.Uncertified -> failure = arc.reason
                    }
                    sourceCurrent = end
                }
                is PathSegmentF32.Close -> sourceCurrent = sourceContourStart
            }
        }
        return failure?.let(DirectedCurveIntervalsResult::Uncertified)
            ?: DirectedCurveIntervalsResult.Ready(intervals)
    }

    private fun subdivideDirectedQuad(
        start: DirectedPointF64,
        control: DirectedPointF64,
        end: DirectedPointF64,
        depthI32: Int,
        destination: MutableList<DirectedCurveInterval>,
    ): String? {
        val bounds = DirectedBoundsF64.of(start, control, end)
        val span = bounds.maximumSpanUpperF64() ?: return "quadratic enclosure overflowed"
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && span > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val startControl = start.midpoint(control) ?: return "quadratic de Casteljau enclosure overflowed"
            val controlEnd = control.midpoint(end) ?: return "quadratic de Casteljau enclosure overflowed"
            val middle = startControl.midpoint(controlEnd) ?: return "quadratic de Casteljau enclosure overflowed"
            return subdivideDirectedQuad(start, startControl, middle, depthI32 + 1, destination)
                ?: subdivideDirectedQuad(middle, controlEnd, end, depthI32 + 1, destination)
        }
        return appendDirectedCurveInterval(
            destination,
            DirectedCurveInterval(bounds, start, end, directedMonotonicY(start, control, end)),
        )
    }

    private fun subdivideDirectedCubic(
        start: DirectedPointF64,
        control1: DirectedPointF64,
        control2: DirectedPointF64,
        end: DirectedPointF64,
        depthI32: Int,
        destination: MutableList<DirectedCurveInterval>,
    ): String? {
        val bounds = DirectedBoundsF64.of(start, control1, control2, end)
        val span = bounds.maximumSpanUpperF64() ?: return "cubic enclosure overflowed"
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && span > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val startControl1 = start.midpoint(control1) ?: return "cubic de Casteljau enclosure overflowed"
            val control1Control2 = control1.midpoint(control2) ?: return "cubic de Casteljau enclosure overflowed"
            val control2End = control2.midpoint(end) ?: return "cubic de Casteljau enclosure overflowed"
            val leftControl2 = startControl1.midpoint(control1Control2) ?: return "cubic de Casteljau enclosure overflowed"
            val rightControl1 = control1Control2.midpoint(control2End) ?: return "cubic de Casteljau enclosure overflowed"
            val middle = leftControl2.midpoint(rightControl1) ?: return "cubic de Casteljau enclosure overflowed"
            return subdivideDirectedCubic(start, startControl1, leftControl2, middle, depthI32 + 1, destination)
                ?: subdivideDirectedCubic(middle, rightControl1, control2End, end, depthI32 + 1, destination)
        }
        return appendDirectedCurveInterval(
            destination,
            DirectedCurveInterval(bounds, start, end, directedMonotonicY(start, control1, control2, end)),
        )
    }

    private fun appendDirectedCurveInterval(
        destination: MutableList<DirectedCurveInterval>,
        interval: DirectedCurveInterval,
    ): String? {
        if (destination.size >= MAX_CERTIFICATE_INTERVALS) {
            return "interval subdivision exceeded the test-only certificate budget"
        }
        destination += interval
        return null
    }

    private fun directedMonotonicY(vararg points: DirectedPointF64): Boolean {
        var increasing = true
        var decreasing = true
        for (indexI32 in 1 until points.size) {
            increasing = increasing && points[indexI32 - 1].y.upperF64 <= points[indexI32].y.lowerF64
            decreasing = decreasing && points[indexI32 - 1].y.lowerF64 >= points[indexI32].y.upperF64
        }
        return increasing || decreasing
    }

    private sealed interface DirectedSvgArcResult {
        data object Line : DirectedSvgArcResult

        data class Ready(val value: DirectedSvgArcF64) : DirectedSvgArcResult

        data class Uncertified(val reason: String) : DirectedSvgArcResult
    }

    /**
     * Directed SVG endpoint-arc conversion for the certificate.  The certified
     * domain intentionally accepts only zero axis rotation: a non-zero rotation
     * is still a valid rendering input, but this test oracle returns
     * Uncertified instead of inventing a tolerance for coupled rotations.
     */
    private fun directedSvgArc(
        start: PointF64,
        segment: PathSegmentF32.ArcTo,
        end: PointF64,
    ): DirectedSvgArcResult {
        if (segment.xAxisRotation != 0f) {
            return DirectedSvgArcResult.Uncertified("SVG arc certificate requires zero axis rotation")
        }
        val startPoint = start.toDirectedPointF64()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc start is non-finite")
        val endPoint = end.toDirectedPointF64()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc end is non-finite")
        var radiusX = DirectedF64.exact(abs(segment.radius.x.toDouble()))
            ?: return DirectedSvgArcResult.Uncertified("SVG arc radius x is non-finite")
        var radiusY = DirectedF64.exact(abs(segment.radius.y.toDouble()))
            ?: return DirectedSvgArcResult.Uncertified("SVG arc radius y is non-finite")
        if (start == end || radiusX.lowerF64 == 0.0 || radiusY.lowerF64 == 0.0) return DirectedSvgArcResult.Line

        // SVG F.6.5 endpoint conversion with cos(phi)=1 and sin(phi)=0.
        val primeX = startPoint.x.minus(endPoint.x)?.times(DIRECTED_HALF)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc prime x overflowed")
        val primeY = startPoint.y.minus(endPoint.y)?.times(DIRECTED_HALF)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc prime y overflowed")
        val radiusXSquared = radiusX.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc radius x squared overflowed")
        val radiusYSquared = radiusY.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc radius y squared overflowed")
        val primeXSquared = primeX.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc prime x squared overflowed")
        val primeYSquared = primeY.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc prime y squared overflowed")
        val lambdaLeft = primeXSquared.dividedBy(radiusXSquared)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc lambda x overflowed")
        val lambdaRight = primeYSquared.dividedBy(radiusYSquared)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc lambda y overflowed")
        val lambda = lambdaLeft.plus(lambdaRight)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc lambda overflowed")
        when {
            lambda.upperF64 <= 1.0 -> Unit
            lambda.lowerF64 > 1.0 -> {
                val scale = lambda.sqrtAfterSvgClamp()
                    ?: return DirectedSvgArcResult.Uncertified("SVG arc radii scale cannot be enclosed")
                radiusX = radiusX.times(scale)
                    ?: return DirectedSvgArcResult.Uncertified("SVG arc radius x scale overflowed")
                radiusY = radiusY.times(scale)
                    ?: return DirectedSvgArcResult.Uncertified("SVG arc radius y scale overflowed")
            }
            else -> return DirectedSvgArcResult.Uncertified("SVG arc radii correction is numerically ambiguous")
        }

        val adjustedRadiusXSquared = radiusX.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc adjusted radius x squared overflowed")
        val adjustedRadiusYSquared = radiusY.squared()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc adjusted radius y squared overflowed")
        val denominatorLeft = adjustedRadiusXSquared.times(primeYSquared)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc denominator x overflowed")
        val denominatorRight = adjustedRadiusYSquared.times(primeXSquared)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc denominator y overflowed")
        val denominator = denominatorLeft.plus(denominatorRight)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc denominator overflowed")
        if (denominator.lowerF64 <= 0.0) {
            return DirectedSvgArcResult.Uncertified("SVG arc denominator can reach zero")
        }
        val numeratorBase = adjustedRadiusXSquared.times(adjustedRadiusYSquared)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc numerator base overflowed")
        val numeratorFirst = numeratorBase.minus(denominatorLeft)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc numerator first subtraction overflowed")
        val numerator = numeratorFirst.minus(denominatorRight)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc numerator second subtraction overflowed")
        var coefficient = numerator.dividedBy(denominator)?.sqrtAfterSvgClamp()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre coefficient cannot be enclosed")
        if (segment.largeArc == segment.sweep) {
            coefficient = coefficient.negated()
                ?: return DirectedSvgArcResult.Uncertified("SVG arc centre coefficient sign overflowed")
        }
        val centerPrimeX = coefficient.times(radiusX.times(primeY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre x product overflowed"))
            ?.dividedBy(radiusY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre x overflowed")
        val centerPrimeY = coefficient.times(radiusY.times(primeX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre y product overflowed"))
            ?.dividedBy(radiusX)
            ?.negated()
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre y overflowed")
        val midpointX = startPoint.x.plus(endPoint.x)?.times(DIRECTED_HALF)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre midpoint x overflowed")
        val midpointY = startPoint.y.plus(endPoint.y)?.times(DIRECTED_HALF)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc centre midpoint y overflowed")
        val center = DirectedPointF64(
            x = centerPrimeX.plus(midpointX)
                ?: return DirectedSvgArcResult.Uncertified("SVG arc centre x addition overflowed"),
            y = centerPrimeY.plus(midpointY)
                ?: return DirectedSvgArcResult.Uncertified("SVG arc centre y addition overflowed"),
        )
        val unitStartX = primeX.minus(centerPrimeX)?.dividedBy(radiusX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc unit start x overflowed")
        val unitStartY = primeY.minus(centerPrimeY)?.dividedBy(radiusY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc unit start y overflowed")
        val unitEndX = primeX.negated()?.minus(centerPrimeX)?.dividedBy(radiusX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc unit end x overflowed")
        val unitEndY = primeY.negated()?.minus(centerPrimeY)?.dividedBy(radiusY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc unit end y overflowed")
        val startAngle = directedAtan2(unitStartY, unitStartX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc start angle crosses an atan2 branch")
        val crossFirst = unitStartX.times(unitEndY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep cross first product overflowed")
        val crossSecond = unitStartY.times(unitEndX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep cross second product overflowed")
        val cross = crossFirst.minus(crossSecond)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep cross overflowed")
        val dotFirst = unitStartX.times(unitEndX)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep dot first product overflowed")
        val dotSecond = unitStartY.times(unitEndY)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep dot second product overflowed")
        val dot = dotFirst.plus(dotSecond)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep dot overflowed")
        val rawSweep = directedAtan2(cross, dot)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep crosses an atan2 branch")
        val sweepAngle = directedSvgSweep(rawSweep, segment.sweep)
            ?: return DirectedSvgArcResult.Uncertified("SVG arc sweep sign is numerically ambiguous")
        return DirectedSvgArcResult.Ready(
            DirectedSvgArcF64(center, radiusX, radiusY, startAngle, sweepAngle),
        )
    }

    private data class DirectedSvgArcF64(
        val center: DirectedPointF64,
        val radiusX: DirectedF64,
        val radiusY: DirectedF64,
        val startAngle: DirectedF64,
        val sweepAngle: DirectedF64,
    ) {
        fun pointAt(transform: DirectedScaleTranslateF64, t: Double): DirectedPointF64? =
            sourcePointAt(angleFor(DirectedF64.exact(t) ?: return null) ?: return null)?.let(transform::map)

        fun bounds(transform: DirectedScaleTranslateF64, fromT: Double, toT: Double): DirectedBoundsF64? {
            val theta = angleFor(DirectedF64.of(fromT, toT) ?: return null) ?: return null
            return sourcePointAt(theta)?.let(transform::map)?.let { DirectedBoundsF64.of(it) }
        }

        fun isMonotonicY(transform: DirectedScaleTranslateF64, fromT: Double, toT: Double): Boolean? {
            val theta = angleFor(DirectedF64.of(fromT, toT) ?: return null) ?: return null
            val cosine = directedCosine(theta) ?: return null
            val derivative = radiusY.times(cosine)?.let { transform.sy.times(it) } ?: return null
            return derivative.lowerF64 > 0.0 || derivative.upperF64 < 0.0
        }

        private fun angleFor(t: DirectedF64): DirectedF64? =
            startAngle.plus(sweepAngle.times(t) ?: return null)

        private fun sourcePointAt(theta: DirectedF64): DirectedPointF64? {
            val cosine = directedCosine(theta) ?: return null
            val sine = directedSine(theta) ?: return null
            return DirectedPointF64(
                x = center.x.plus(radiusX.times(cosine) ?: return null) ?: return null,
                y = center.y.plus(radiusY.times(sine) ?: return null) ?: return null,
            )
        }
    }

    private fun subdivideDirectedArc(
        arc: DirectedSvgArcF64,
        transform: DirectedScaleTranslateF64,
        fromT: Double,
        toT: Double,
        depthI32: Int,
        destination: MutableList<DirectedCurveInterval>,
    ): String? {
        val bounds = arc.bounds(transform, fromT, toT) ?: return "SVG arc bounds cannot be enclosed"
        val span = bounds.maximumSpanUpperF64() ?: return "SVG arc enclosure overflowed"
        if (depthI32 < ORACLE_MAX_SUBDIVISION_DEPTH && span > CERTIFICATE_INTERVAL_MAXIMUM_SPAN) {
            val middle = (fromT + toT) * 0.5
            return subdivideDirectedArc(arc, transform, fromT, middle, depthI32 + 1, destination)
                ?: subdivideDirectedArc(arc, transform, middle, toT, depthI32 + 1, destination)
        }
        val start = arc.pointAt(transform, fromT) ?: return "SVG arc start cannot be enclosed"
        val end = arc.pointAt(transform, toT) ?: return "SVG arc end cannot be enclosed"
        val monotonicY = arc.isMonotonicY(transform, fromT, toT) ?: return "SVG arc derivative cannot be enclosed"
        return appendDirectedCurveInterval(destination, DirectedCurveInterval(bounds, start, end, monotonicY))
    }

    /**
     * StrictMath is specified within one ulp for these transcendental results.
     * We take two adjacent F64 values to cover that ulp and the directed
     * endpoint.  If a finite critical-point enumeration cannot be formed, the
     * caller receives Uncertified rather than a widened epsilon.
     */
    private fun directedSine(angle: DirectedF64): DirectedF64? = directedTrig(angle, sine = true)

    private fun directedCosine(angle: DirectedF64): DirectedF64? = directedTrig(angle, sine = false)

    private fun directedTrig(angle: DirectedF64, sine: Boolean): DirectedF64? {
        if ((angle.spanUpperF64() ?: return null) >= DIRECTED_TWO_PI.lowerF64) return DirectedF64.of(-1.0, 1.0)
        val first = transcendentalEnclosure(if (sine) StrictMath.sin(angle.lowerF64) else StrictMath.cos(angle.lowerF64))
            ?: return null
        val second = transcendentalEnclosure(if (sine) StrictMath.sin(angle.upperF64) else StrictMath.cos(angle.upperF64))
            ?: return null
        var lower = minOf(first.lowerF64, second.lowerF64)
        var upper = maxOf(first.upperF64, second.upperF64)
        val offset = if (sine) DIRECTED_HALF_PI else DIRECTED_ZERO
        val indices = directedCriticalIndices(angle, offset) ?: return null
        for (indexI64 in indices) {
            val multiple = DirectedF64.exact(indexI64.toDouble()) ?: return null
            val critical = offset.plus(DIRECTED_PI.times(multiple) ?: return null) ?: return null
            if (critical.overlaps(angle)) {
                val extremum = if (indexI64 and 1L == 0L) 1.0 else -1.0
                lower = minOf(lower, extremum)
                upper = maxOf(upper, extremum)
            }
        }
        return DirectedF64.of(lower, upper)
    }

    private fun directedCriticalIndices(angle: DirectedF64, offset: DirectedF64): LongRange? {
        val relative = angle.minus(offset)?.dividedBy(DIRECTED_PI) ?: return null
        val lowerFloor = floor(relative.lowerF64)
        val upperCeiling = ceil(relative.upperF64)
        if (
            !lowerFloor.isFinite() || !upperCeiling.isFinite() ||
            lowerFloor < -MAX_EXACT_TRIG_INDEX_F64 || upperCeiling > MAX_EXACT_TRIG_INDEX_F64
        ) return null
        val first = lowerFloor.toLong() - 2L
        val last = upperCeiling.toLong() + 2L
        if (last < first || last - first > MAX_TRIG_CRITICAL_CANDIDATES) return null
        return first..last
    }

    private fun directedAtan2(y: DirectedF64, x: DirectedF64): DirectedF64? {
        val yContainsZero = y.lowerF64 <= 0.0 && y.upperF64 >= 0.0
        val xContainsZero = x.lowerF64 <= 0.0 && x.upperF64 >= 0.0
        if ((xContainsZero && yContainsZero) || (x.lowerF64 < 0.0 && yContainsZero)) return null
        val corners = listOf(
            transcendentalEnclosure(StrictMath.atan2(y.lowerF64, x.lowerF64)),
            transcendentalEnclosure(StrictMath.atan2(y.lowerF64, x.upperF64)),
            transcendentalEnclosure(StrictMath.atan2(y.upperF64, x.lowerF64)),
            transcendentalEnclosure(StrictMath.atan2(y.upperF64, x.upperF64)),
        )
        if (corners.any { it == null }) return null
        val values = corners.filterNotNull()
        val result = DirectedF64.of(values.minOf(DirectedF64::lowerF64), values.maxOf(DirectedF64::upperF64))
            ?: return null
        return if ((result.spanUpperF64() ?: return null) < DIRECTED_PI.lowerF64) result else null
    }

    private fun transcendentalEnclosure(value: Double): DirectedF64? {
        if (!value.isFinite()) return null
        return DirectedF64.of(downTwice(value) ?: return null, upTwice(value) ?: return null)
    }

    private fun directedSvgSweep(rawSweep: DirectedF64, sweep: Boolean): DirectedF64? = when {
        rawSweep.lowerF64 > 0.0 && !sweep -> rawSweep.minus(DIRECTED_TWO_PI)
        rawSweep.upperF64 < 0.0 && sweep -> rawSweep.plus(DIRECTED_TWO_PI)
        rawSweep.lowerF64 > 0.0 && sweep -> rawSweep
        rawSweep.upperF64 < 0.0 && !sweep -> rawSweep
        else -> null
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
    private const val MAX_TRIG_CRITICAL_CANDIDATES = 16L
    private const val MAX_EXACT_TRIG_INDEX_F64 = 4_503_599_627_370_496.0

    /* Math.PI is a nearest F64 constant; adjacent directed values enclose π. */
    private val DIRECTED_ZERO: DirectedF64 = DirectedF64.exact(0.0)!!
    private val DIRECTED_HALF: DirectedF64 = DirectedF64.exact(0.5)!!
    private val DIRECTED_PI: DirectedF64 = DirectedF64.of(down(StrictMath.PI)!!, up(StrictMath.PI)!!)!!
    private val DIRECTED_HALF_PI: DirectedF64 = DIRECTED_PI.dividedBy(DirectedF64.exact(2.0)!!)!!
    private val DIRECTED_TWO_PI: DirectedF64 = DIRECTED_PI.times(DirectedF64.exact(2.0)!!)!!
}
