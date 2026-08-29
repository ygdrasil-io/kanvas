package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.floor

/** Independent pixel-center oracle for an integral round-cap stroke intersected with a device scissor. */
class SurfaceSrgbRoundCapStrokeScissorCpuOracle(
    private val startX: Double,
    private val endX: Double,
    private val centerY: Double,
    private val radius: Double,
    private val color: IntArray,
    private val clipLeft: Int,
    private val clipTop: Int,
    private val clipRight: Int,
    private val clipBottom: Int,
) : CpuOracle {
    init {
        require(startX.isIntegralDeviceCoordinate() && endX.isIntegralDeviceCoordinate() &&
            centerY.isIntegralDeviceCoordinate() && radius.isFinite() && radius > 0.0 && endX - startX >= 2.0 * radius
        ) { "round-cap scissor oracle requires an integral left-to-right horizontal segment with a positive radius" }
        require(color.size == 4 && color.all { it in 0..255 }) { "color must be RGBA byte channels" }
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
                val inBody = sampleX in startX..endX && sampleY in (centerY - radius)..(centerY + radius)
                val inStartCap = squaredDistance(sampleX, sampleY, startX, centerY) <= radius * radius
                val inEndCap = squaredDistance(sampleX, sampleY, endX, centerY) <= radius * radius
                if (inBody || inStartCap || inEndCap) {
                    val offset = (y * width + x) * 4
                    color.indices.forEach { channel -> output[offset + channel] = color[channel].toByte() }
                }
            }
        }
    }

    private fun squaredDistance(x: Double, y: Double, centerX: Double, centerY: Double): Double =
        (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)

    private fun Double.isIntegralDeviceCoordinate(): Boolean = isFinite() && floor(this) == this
}
