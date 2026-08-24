package org.graphiks.kanvas.gpu.evidence.oracle

/**
 * Independent CPU oracle for the bounded non-AA stroke route: four disjoint device-space
 * coverage bands painted with one clamp linear gradient evaluated at original device pixels.
 */
class SurfaceSrgbLinearGradientStrokeBandsCpuOracle(
    private val bands: List<Rect>,
    private val start: Point,
    private val end: Point,
    private val first: Stop,
    private val last: Stop,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        init { require(left < right && top < bottom) { "stroke band must have positive area" } }
        fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
    }

    data class Point(val x: Double, val y: Double) {
        init { require(x.isFinite() && y.isFinite()) { "gradient point must be finite" } }
    }

    data class Stop(val position: Double, val red: Int, val green: Int, val blue: Int) {
        init {
            require(position.isFinite() && position in 0.0..1.0) { "gradient stop position must be in the unit interval" }
            require(listOf(red, green, blue).all { it in 0..255 }) { "gradient stop channels must be unsigned bytes" }
        }
    }

    init {
        require(bands.size == 4) { "stroke oracle requires exactly four coverage bands" }
        require(first.position == 0.0 && last.position == 1.0) { "stroke oracle requires endpoint stops" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "target dimensions must be positive" }
        val output = ByteArray(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        require(lengthSquared.isFinite() && lengthSquared > 0.0) { "stroke oracle requires a non-degenerate gradient axis" }
        for (y in 0 until height) for (x in 0 until width) {
            if (!bands.any { it.contains(x, y) }) continue
            val t = (((x + .5 - start.x) * dx + (y + .5 - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val source = interpolate(t)
            val rgba = SurfaceSrgbOracleMath.storeSrgb(source)
            val offset = (y * width + x) * 4
            for (channel in rgba.indices) output[offset + channel] = rgba[channel].toByte()
        }
        return output
    }

    private fun interpolate(t: Double): SurfaceSrgbOracleMath.LinearPremul {
        val startColor = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(first.red, first.green, first.blue, 255))
        val endColor = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(last.red, last.green, last.blue, 255))
        return SurfaceSrgbOracleMath.LinearPremul(
            startColor.red + (endColor.red - startColor.red) * t,
            startColor.green + (endColor.green - startColor.green) * t,
            startColor.blue + (endColor.blue - startColor.blue) * t,
            1.0,
        )
    }
}
