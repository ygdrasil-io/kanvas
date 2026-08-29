package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2

/** Independent pixel-center CPU oracle for W41's bounded three-stop sweep rectangle stroke. */
class SurfaceSrgbThreeStopSweepGradientStrokeCpuOracle(
    private val bands: List<Rect>,
    private val center: Point,
    stops: List<Stop>,
) : CpuOracle {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }
    data class Point(val x: Double, val y: Double)
    data class Stop(val position: Double, val red: Int, val green: Int, val blue: Int, val alpha: Int = 255)

    private val decodedStops: List<Pair<Double, SurfaceSrgbOracleMath.LinearPremul>>

    init {
        require(bands.size == 4 && stops.size == 3)
        require(stops.first().position == 0.0 && stops.last().position == 1.0 && stops.zipWithNext().all { it.first.position < it.second.position })
        decodedStops = stops.map { stop ->
            stop.position to SurfaceSrgbOracleMath.decodeStraight(intArrayOf(stop.red, stop.green, stop.blue, stop.alpha))
        }
    }

    override fun render(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            if (!bands.any { it.contains(x, y) }) continue
            val t = ((atan2(y + .5 - center.y, x + .5 - center.x) * 180.0 / PI + 360.0) % 360.0) / 360.0
            val upper = decodedStops.indexOfFirst { t <= it.first }.let { if (it < 0) decodedStops.lastIndex else it }
            val (leftPosition, left) = decodedStops[if (upper == 0) 0 else upper - 1]
            val (rightPosition, right) = decodedStops[upper]
            val localT = if (upper == 0) 0.0 else (t - leftPosition) / (rightPosition - leftPosition)
            val rgba = SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.LinearPremul(
                left.red + (right.red - left.red) * localT,
                left.green + (right.green - left.green) * localT,
                left.blue + (right.blue - left.blue) * localT,
                left.alpha + (right.alpha - left.alpha) * localT,
            ))
            val offset = (y * width + x) * 4
            rgba.indices.forEach { out[offset + it] = rgba[it].toByte() }
        }
        return out
    }
}
