package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.floor

/**
 * Independent pixel-center oracle for W25's pixel-exact opaque round-cap segment.
 *
 * It intentionally evaluates the geometric union directly instead of reusing
 * Kanvas stroke expansion or its stencil fan representation. Its inputs are
 * deliberately constrained to the only proven native tessellation domain:
 * radius two, integral device grid, horizontal left-to-right segment and no
 * overlap between the two caps.
 */
class SurfaceSrgbRoundCapStrokeCpuOracle(
    private val startX: Double,
    private val endX: Double,
    private val centerY: Double,
    private val radius: Double,
    private val rgba: IntArray,
) : CpuOracle {
    init {
        require(startX.isIntegralDeviceCoordinate() && endX.isIntegralDeviceCoordinate() &&
            centerY.isIntegralDeviceCoordinate() && radius == 2.0 && endX - startX >= 4.0
        ) { "W25 round-cap oracle requires an integral left-to-right radius-two horizontal segment" }
        require(rgba.size == 4 && rgba.all { it in 0..255 })
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0)
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val sampleX = x + 0.5
                val sampleY = y + 0.5
                val inBody = sampleX in startX..endX && sampleY in (centerY - radius)..(centerY + radius)
                val inStartCap = squaredDistance(sampleX, sampleY, startX, centerY) <= radius * radius
                val inEndCap = squaredDistance(sampleX, sampleY, endX, centerY) <= radius * radius
                if (inBody || inStartCap || inEndCap) {
                    val offset = (y * width + x) * 4
                    rgba.indices.forEach { channel -> output[offset + channel] = rgba[channel].toByte() }
                }
            }
        }
    }

    private fun squaredDistance(x: Double, y: Double, centerX: Double, centerY: Double): Double =
        (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)

    private fun Double.isIntegralDeviceCoordinate(): Boolean = isFinite() && floor(this) == this
}
