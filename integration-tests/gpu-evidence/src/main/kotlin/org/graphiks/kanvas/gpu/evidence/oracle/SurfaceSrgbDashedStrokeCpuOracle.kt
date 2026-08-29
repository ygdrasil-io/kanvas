package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent pixel-center oracle for one finite dashed device-space stroke. */
class SurfaceSrgbDashedStrokeCpuOracle(
    private val strokeStartX: Double,
    private val strokeStartY: Double,
    private val strokeEndX: Double,
    private val strokeEndY: Double,
    private val strokeWidth: Double,
    dashIntervals: List<Double>,
    private val dashPhase: Double,
    private val color: IntArray,
) : CpuOracle {
    private val intervals = dashIntervals.toList()
    private val dx = strokeEndX - strokeStartX
    private val dy = strokeEndY - strokeStartY
    private val lengthSquared = dx * dx + dy * dy
    private val pathLength = sqrt(lengthSquared)
    private val patternLength = intervals.sum()

    init {
        require(listOf(strokeStartX, strokeStartY, strokeEndX, strokeEndY, strokeWidth, dashPhase).all(Double::isFinite)) {
            "stroke and dash geometry must be finite"
        }
        require(strokeWidth > 0.0 && lengthSquared > 0.0) {
            "stroke must be positive and non-degenerate"
        }
        require(intervals.isNotEmpty() && intervals.all { it >= 0.0 } && patternLength > 0.0) {
            "dash pattern must contain a positive interval"
        }
        require(color.size == 4 && color.all { it in 0..255 }) { "color must be RGBA byte channels" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0)
        val halfWidthSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val px = x + 0.5
                val py = y + 0.5
                val projection = ((px - strokeStartX) * dx + (py - strokeStartY) * dy) / lengthSquared
                if (projection !in 0.0..1.0) continue
                val closestX = strokeStartX + projection * dx
                val closestY = strokeStartY + projection * dy
                val distanceX = px - closestX
                val distanceY = py - closestY
                if (distanceX * distanceX + distanceY * distanceY > halfWidthSquared) continue
                if (!isDashOn(projection * pathLength)) continue
                val offset = (y * width + x) * 4
                color.indices.forEach { channel -> output[offset + channel] = color[channel].toByte() }
            }
        }
    }

    private fun isDashOn(distance: Double): Boolean {
        var position = (distance + dashPhase) % patternLength
        if (position < 0.0) position += patternLength
        var accumulated = 0.0
        intervals.forEachIndexed { index, length ->
            val next = accumulated + length
            if (position < next || index == intervals.lastIndex && position <= next) {
                return index % 2 == 0
            }
            accumulated = next
        }
        return true
    }
}
