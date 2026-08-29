package org.graphiks.kanvas.gpu.evidence.oracle

/**
 * Independent CPU oracle for W37: a non-AA four-band rectangle stroke painted by a
 * three-stop clamp linear gradient in sRGB Surface coordinates.
 */
class SurfaceSrgbThreeStopLinearGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    private val start: Point,
    private val end: Point,
    private val stops: List<Stop>,
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
        require(stops.size == 3 && stops.first().position == 0.0 && stops.last().position == 1.0) {
            "W37 stroke oracle requires three ordered endpoint stops"
        }
        require(stops.zipWithNext().all { (left, right) -> left.position < right.position }) {
            "W37 stroke stops must be strictly ordered"
        }
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
            val rgba = SurfaceSrgbOracleMath.storeSrgb(interpolate(t))
            val offset = (y * width + x) * 4
            for (channel in rgba.indices) output[offset + channel] = rgba[channel].toByte()
        }
        return output
    }

    private fun interpolate(t: Double): SurfaceSrgbOracleMath.LinearPremul {
        val index = if (t <= stops[1].position) 0 else 1
        val first = stops[index]
        val last = stops[index + 1]
        val localT = ((t - first.position) / (last.position - first.position)).coerceIn(0.0, 1.0)
        val from = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(first.red, first.green, first.blue, 255))
        val to = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(last.red, last.green, last.blue, 255))
        return SurfaceSrgbOracleMath.LinearPremul(
            from.red + (to.red - from.red) * localT,
            from.green + (to.green - from.green) * localT,
            from.blue + (to.blue - from.blue) * localT,
            1.0,
        )
    }
}
