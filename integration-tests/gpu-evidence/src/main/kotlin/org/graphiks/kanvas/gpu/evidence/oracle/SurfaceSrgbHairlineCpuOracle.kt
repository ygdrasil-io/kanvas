package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.floor

/** Independent exact oracle for a horizontal device-space hairline quad. */
class SurfaceSrgbHairlineCpuOracle(
    private val startX: Double,
    private val endX: Double,
    private val centerY: Double,
    private val color: IntArray,
) : CpuOracle {
    init {
        require(startX.isFinite() && endX.isFinite() && centerY.isFinite()) { "hairline geometry must be finite" }
        require(floor(startX) == startX && floor(endX) == endX && floor(centerY) == centerY) {
            "hairline oracle requires integral device coordinates"
        }
        require(endX > startX) { "hairline segment must be non-empty" }
        require(color.size == 4 && color.all { it in 0..255 }) { "color must be RGBA byte channels" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0)
        val row = centerY.toInt()
        return ByteArray(width * height * 4).also { output ->
            if (row !in 0 until height) return@also
            for (x in 0 until width) {
                val sampleX = x + 0.5
                if (sampleX < startX || sampleX >= endX) continue
                val offset = (row * width + x) * 4
                color.indices.forEach { channel -> output[offset + channel] = color[channel].toByte() }
            }
        }
    }
}
