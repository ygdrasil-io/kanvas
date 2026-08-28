package org.graphiks.kanvas.gpu.evidence.oracle

/**
 * Independent pixel-center oracle for the bounded opaque round-cap segment.
 *
 * It intentionally evaluates the geometric union directly instead of reusing
 * Kanvas stroke expansion or its stencil fan representation.
 */
class SurfaceSrgbRoundCapStrokeCpuOracle(
    private val startX: Double,
    private val endX: Double,
    private val centerY: Double,
    private val radius: Double,
    private val rgba: IntArray,
) : CpuOracle {
    init {
        require(startX.isFinite() && endX.isFinite() && startX < endX)
        require(centerY.isFinite() && radius.isFinite() && radius > 0.0)
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
}
