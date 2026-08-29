package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2

/** Independent pixel-center CPU oracle for W39's bounded sweep rectangle stroke. */
class SurfaceSrgbTwoStopSweepGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    private val center: Point,
    private val start: IntArray,
    private val end: IntArray,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }
    data class Point(val x: Double, val y: Double)

    init { require(bands.size == 4 && start.size == 4 && end.size == 4) }

    override fun render(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        val startLinear = SurfaceSrgbOracleMath.decodeStraight(start)
        val endLinear = SurfaceSrgbOracleMath.decodeStraight(end)
        for (y in 0 until height) for (x in 0 until width) {
            if (!bands.any { it.contains(x, y) }) continue
            val angle = (atan2(y + .5 - center.y, x + .5 - center.x) * 180.0 / PI + 360.0) % 360.0
            val t = angle / 360.0
            val rgba = SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(
                startLinear.red + (endLinear.red - startLinear.red) * t,
                startLinear.green + (endLinear.green - startLinear.green) * t,
                startLinear.blue + (endLinear.blue - startLinear.blue) * t,
                1.0,
            ))
            val offset = (y * width + x) * 4
            rgba.indices.forEach { out[offset + it] = rgba[it].toByte() }
        }
        return out
    }
}
