package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent pixel-center oracle for one opaque device-space path stroke. */
class SurfaceSrgbSolidStrokeCpuOracle(
    private val strokeStartX: Double,
    private val strokeStartY: Double,
    private val strokeEndX: Double,
    private val strokeEndY: Double,
    private val strokeWidth: Double,
    private val color: IntArray,
    private val squareCaps: Boolean = false,
) : CpuOracle {
    private val dx = strokeEndX - strokeStartX
    private val dy = strokeEndY - strokeStartY
    private val lengthSquared = dx * dx + dy * dy

    init {
        require(listOf(strokeStartX, strokeStartY, strokeEndX, strokeEndY, strokeWidth).all(Double::isFinite)) {
            "stroke geometry must be finite"
        }
        require(strokeWidth > 0.0 && lengthSquared > 0.0) {
            "stroke must be positive and non-degenerate"
        }
        require(color.size == 4 && color.all { it in 0..255 }) { "color must be RGBA byte channels" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0)
        val halfWidthSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        val capExtension = if (squareCaps) sqrt(halfWidthSquared / lengthSquared) else 0.0
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val px = x + 0.5
                val py = y + 0.5
                val projection = ((px - strokeStartX) * dx + (py - strokeStartY) * dy) / lengthSquared
                if (projection !in -capExtension..(1.0 + capExtension)) continue
                val closestX = strokeStartX + projection * dx
                val closestY = strokeStartY + projection * dy
                val distanceX = px - closestX
                val distanceY = py - closestY
                if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                val offset = (y * width + x) * 4
                color.indices.forEach { channel -> output[offset + channel] = color[channel].toByte() }
            }
        }
    }
}
