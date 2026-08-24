package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Independent Surface-sRGB reference for the bounded two-stop gradient fixtures.
 *
 * The geometry is evaluated at pixel centers. Stop colors are decoded to linear light,
 * premultiplied, interpolated without intermediate quantization, and encoded only when
 * the final Surface pixel is stored.
 */
class SurfaceSrgbGradientCpuOracle private constructor(
    private val drawBounds: Rect,
    stops: List<Stop>,
    private val rawTAt: (Double, Double) -> Double,
) : CpuOracle {
    data class Point(val x: Float, val y: Float) {
        init {
            require(x.isFinite() && y.isFinite()) { "gradient points must be finite" }
        }
    }

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        init {
            require(listOf(left, top, right, bottom).all(Float::isFinite)) { "gradient bounds must be finite" }
            require(left <= right && top <= bottom) { "gradient bounds must not be inverted" }
        }

        fun contains(x: Double, y: Double): Boolean =
            x >= left && x < right && y >= top && y < bottom
    }

    data class Stop(
        val position: Float,
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int = 255,
    ) {
        init {
            require(position.isFinite()) { "gradient stop position must be finite" }
            require(listOf(red, green, blue, alpha).all { it in 0..255 }) { "gradient stop channels must be unsigned bytes" }
        }
    }

    private val decodedStops: List<Pair<Float, SurfaceSrgbOracleMath.LinearPremul>>

    init {
        require(stops.size in 1..16 && stops.all { it.position in 0f..1f } &&
            (stops.size == 1 || (stops.first().position == 0f && stops.last().position == 1f &&
                stops.zipWithNext().all { (left, right) -> left.position < right.position }))
        ) {
            "oracle requires one through sixteen ordered stops in the unit interval"
        }
        require(stops.all { it.alpha == 255 }) { "oracle requires opaque stops" }
        decodedStops = stops.map { it.position to it.decode() }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "target dimensions must be positive" }
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount <= Int.MAX_VALUE.toLong() / 4L) { "target dimensions exceed RGBA8 byte capacity" }
        val output = ByteArray((pixelCount * 4L).toInt())
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            if (!drawBounds.contains(px, py)) continue
            val color = interpolate(rawTAt(px, py).coerceIn(0.0, 1.0))
            val stored = SurfaceSrgbOracleMath.storeSrgb(color)
            val offset = ((y.toLong() * width.toLong() + x.toLong()) * 4L).toInt()
            for (channel in stored.indices) output[offset + channel] = stored[channel].toByte()
        }
        return output
    }

    private fun interpolate(t: Double): SurfaceSrgbOracleMath.LinearPremul {
        val upperIndex = decodedStops.indexOfFirst { (position, _) -> t <= position.toDouble() }.let { if (it < 0) decodedStops.lastIndex else it }
        if (upperIndex == 0) return decodedStops.first().second
        val (startPosition, start) = decodedStops[upperIndex - 1]
        val (endPosition, end) = decodedStops[upperIndex]
        return interpolate(start, end, ((t - startPosition) / (endPosition - startPosition)).coerceIn(0.0, 1.0))
    }

    private fun interpolate(
        start: SurfaceSrgbOracleMath.LinearPremul,
        end: SurfaceSrgbOracleMath.LinearPremul,
        t: Double,
    ): SurfaceSrgbOracleMath.LinearPremul =
        SurfaceSrgbOracleMath.LinearPremul(
            start.red + (end.red - start.red) * t,
            start.green + (end.green - start.green) * t,
            start.blue + (end.blue - start.blue) * t,
            start.alpha + (end.alpha - start.alpha) * t,
        )

    private fun Stop.decode(): SurfaceSrgbOracleMath.LinearPremul =
        SurfaceSrgbOracleMath.decodeStraight(intArrayOf(red, green, blue, alpha))

    companion object {
        fun linear(drawBounds: Rect, start: Point, end: Point, stops: List<Stop>): SurfaceSrgbGradientCpuOracle {
            val dx = end.x.toDouble() - start.x.toDouble()
            val dy = end.y.toDouble() - start.y.toDouble()
            val lengthSquared = dx * dx + dy * dy
            require(lengthSquared.isFinite()) { "linear geometry length must be finite" }
            return SurfaceSrgbGradientCpuOracle(drawBounds, stops.toList()) { x, y ->
                if (lengthSquared <= 0.0) 0.0 else {
                    ((x - start.x) * dx + (y - start.y) * dy) / lengthSquared
                }
            }
        }

        fun radial(drawBounds: Rect, center: Point, radius: Float, stops: List<Stop>): SurfaceSrgbGradientCpuOracle {
            require(radius.isFinite() && radius >= 0f) { "radial radius must be finite and nonnegative" }
            return SurfaceSrgbGradientCpuOracle(drawBounds, stops.toList()) { x, y ->
                if (radius <= 0f) 0.0 else {
                    val dx = x - center.x
                    val dy = y - center.y
                    sqrt(dx * dx + dy * dy) / radius
                }
            }
        }

        fun sweep(
            drawBounds: Rect,
            center: Point,
            startAngle: Float,
            endAngle: Float,
            stops: List<Stop>,
        ): SurfaceSrgbGradientCpuOracle {
            require(startAngle.isFinite() && endAngle.isFinite()) { "sweep angles must be finite" }
            val sweep = endAngle.toDouble() - startAngle.toDouble()
            require(sweep in 0.0..360.0) { "sweep span must be in [0, 360] degrees" }
            val startRadians = startAngle.toDouble() * PI / 180.0
            return SurfaceSrgbGradientCpuOracle(drawBounds, stops.toList()) { x, y ->
                if (sweep == 0.0 || (x == center.x.toDouble() && y == center.y.toDouble())) 0.0 else {
                    val normalizedAngle = positiveFract((atan2(y - center.y, x - center.x) - startRadians) / (2.0 * PI))
                    normalizedAngle / (sweep / 360.0)
                }
            }
        }

        private fun positiveFract(value: Double): Double = value - floor(value)
    }
}
