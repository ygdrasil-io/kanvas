package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center CPU oracle for W38's bounded radial rectangle stroke. */
class SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    private val center: Point,
    private val radius: Double,
    private val inner: IntArray,
    private val outer: IntArray,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }
    data class Point(val x: Double, val y: Double)
    init { require(bands.size == 4 && radius > 0.0 && inner.size == 4 && outer.size == 4) }
    override fun render(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            if (!bands.any { it.contains(x, y) }) continue
            val t = (kotlin.math.hypot(x + .5 - center.x, y + .5 - center.y) / radius).coerceIn(0.0, 1.0)
            val a = SurfaceSrgbOracleMath.decodeStraight(inner)
            val b = SurfaceSrgbOracleMath.decodeStraight(outer)
            val rgba = SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(
                a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
                a.blue + (b.blue - a.blue) * t, 1.0,
            ))
            val offset = (y * width + x) * 4
            rgba.indices.forEach { out[offset + it] = rgba[it].toByte() }
        }
        return out
    }
}
