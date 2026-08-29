package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent W45 oracle: scales four bands and interpolates the fixed three-stop axis in sRGB. */
class SurfaceSrgbScaledThreeStopLinearGradientStrokeCpuOracle(
    private val bands: List<Rect>, start: Point, end: Point, scale: Int, translation: Point,
    private val first: Stop, private val middle: Stop, private val last: Stop,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }

    data class Point(val x: Double, val y: Double)
    data class Stop(val red: Int, val green: Int, val blue: Int)

    private val start = Point(start.x * scale + translation.x, start.y * scale + translation.y)
    private val end = Point(end.x * scale + translation.x, end.y * scale + translation.y)

    init {
        require(bands.size == 4 && bands.all { it.left < it.right && it.top < it.bottom })
        require(scale > 0)
        require(start.x.isFinite() && start.y.isFinite() && end.x.isFinite() && end.y.isFinite())
        require(translation.x.isFinite() && translation.y.isFinite())
        require(this.start != this.end)
        require(listOf(first, middle, last).all { it.red in 0..255 && it.green in 0..255 && it.blue in 0..255 })
    }

    override fun render(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val denominator = dx * dx + dy * dy
        val colors = listOf(first, middle, last).map { stop ->
            SurfaceSrgbOracleMath.decodeStraight(intArrayOf(stop.red, stop.green, stop.blue, 255))
        }
        for (y in 0 until height) for (x in 0 until width) if (bands.any { it.contains(x, y) }) {
            val t = (((x + .5 - start.x) * dx + (y + .5 - start.y) * dy) / denominator).coerceIn(0.0, 1.0)
            val (left, right, localT) = if (t <= .5) Triple(colors[0], colors[1], t * 2.0) else Triple(colors[1], colors[2], (t - .5) * 2.0)
            val pixel = SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(
                left.red + (right.red - left.red) * localT,
                left.green + (right.green - left.green) * localT,
                left.blue + (right.blue - left.blue) * localT,
                1.0,
            ))
            val offset = (y * width + x) * 4
            pixel.indices.forEach { out[offset + it] = pixel[it].toByte() }
        }
        return out
    }
}
