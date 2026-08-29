package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.floor

/** Independent pixel-center oracle for a vertical round-cap stroke intersected with a device scissor. */
class SurfaceSrgbRoundCapVerticalStrokeScissorCpuOracle(
    private val startY: Double,
    private val endY: Double,
    private val centerX: Double,
    private val radius: Double,
    private val rgba: IntArray,
    private val clipLeft: Int,
    private val clipTop: Int,
    private val clipRight: Int,
    private val clipBottom: Int,
) : CpuOracle {
    init {
        require(startY.isIntegralDeviceCoordinate() && endY.isIntegralDeviceCoordinate() &&
            centerX.isIntegralDeviceCoordinate() && radius == 2.0 && endY - startY >= 4.0
        ) { "round-cap scissor oracle requires an integral bottom-to-top radius-two vertical segment" }
        require(rgba.size == 4 && rgba.all { it in 0..255 }) { "color must be RGBA byte channels" }
        require(clipLeft >= 0 && clipTop >= 0 && clipRight > clipLeft && clipBottom > clipTop) {
            "scissor must be a non-empty integral device rectangle"
        }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0)
        require(clipRight <= width && clipBottom <= height) { "scissor must fit the target" }
        return ByteArray(width * height * 4).also { output ->
            for (y in clipTop until clipBottom) for (x in clipLeft until clipRight) {
                val sampleX = x + 0.5
                val sampleY = y + 0.5
                val inBody = sampleX in (centerX - radius)..(centerX + radius) && sampleY in startY..endY
                val inStartCap = squaredDistance(sampleX, sampleY, centerX, startY) <= radius * radius
                val inEndCap = squaredDistance(sampleX, sampleY, centerX, endY) <= radius * radius
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
