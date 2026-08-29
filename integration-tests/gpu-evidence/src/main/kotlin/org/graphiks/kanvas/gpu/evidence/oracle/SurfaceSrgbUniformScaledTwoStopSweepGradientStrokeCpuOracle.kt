package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2

/** Independent device-space oracle for a uniformly scaled full sweep stroke. */
class SurfaceSrgbUniformScaledTwoStopSweepGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    private val deviceCenter: Point,
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
            val degrees = (atan2(y + .5 - deviceCenter.y, x + .5 - deviceCenter.x) * 180.0 / PI + 360.0) % 360.0
            val t = degrees / 360.0
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
