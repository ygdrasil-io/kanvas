package org.graphiks.kanvas.gpu.evidence.oracle

/**
 * Independent CPU oracle for W42: four translated device bands painted by a two-stop
 * clamp linear gradient. The gradient axis is translated separately from the coverage.
 */
class SurfaceSrgbTranslatedTwoStopLinearGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    start: Point,
    end: Point,
    translation: Vector,
    private val first: Stop,
    private val last: Stop,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
    }
    data class Point(val x: Double, val y: Double)
    data class Vector(val x: Double, val y: Double)
    data class Stop(val red: Int, val green: Int, val blue: Int)

    private val translatedStart = Point(start.x + translation.x, start.y + translation.y)
    private val translatedEnd = Point(end.x + translation.x, end.y + translation.y)

    init {
        require(bands.size == 4 && bands.all { it.left < it.right && it.top < it.bottom })
        require(listOf(first.red, first.green, first.blue, last.red, last.green, last.blue).all { it in 0..255 })
        require(listOf(start.x, start.y, end.x, end.y, translation.x, translation.y).all(Double::isFinite))
    }

    override fun render(width: Int, height: Int): ByteArray {
        val output = ByteArray(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        val dx = translatedEnd.x - translatedStart.x
        val dy = translatedEnd.y - translatedStart.y
        val lengthSquared = dx * dx + dy * dy
        require(lengthSquared > 0.0 && lengthSquared.isFinite())
        val from = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(first.red, first.green, first.blue, 255))
        val to = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(last.red, last.green, last.blue, 255))
        for (y in 0 until height) for (x in 0 until width) {
            if (!bands.any { it.contains(x, y) }) continue
            val t = (((x + .5 - translatedStart.x) * dx + (y + .5 - translatedStart.y) * dy) / lengthSquared)
                .coerceIn(0.0, 1.0)
            val rgba = SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(
                from.red + (to.red - from.red) * t,
                from.green + (to.green - from.green) * t,
                from.blue + (to.blue - from.blue) * t,
                1.0,
            ))
            val offset = (y * width + x) * 4
            rgba.indices.forEach { output[offset + it] = rgba[it].toByte() }
        }
        return output
    }
}
