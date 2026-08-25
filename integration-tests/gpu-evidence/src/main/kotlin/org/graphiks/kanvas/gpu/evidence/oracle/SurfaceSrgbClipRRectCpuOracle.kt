package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center oracle for one hard uniform RRect clip and opaque rectangle draws. */
class SurfaceSrgbClipRRectCpuOracle(
    background: IntArray,
    val clip: DeviceRRect,
    draws: List<OpaqueRect>,
) : CpuOracle {
    companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
    }

    data class DeviceRRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radiusX: Float,
        val radiusY: Float,
    ) {
        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                "RRect bounds must be finite"
            }
            require(right > left && bottom > top) { "RRect bounds must be non-empty" }
            require(radiusX.isFinite() && radiusY.isFinite() && radiusX >= 0f && radiusY >= 0f) {
                "RRect radii must be finite and non-negative"
            }
            require(radiusX * 2f <= right - left && radiusY * 2f <= bottom - top) {
                "uniform RRect radii must fit bounds"
            }
        }
    }

    data class OpaqueRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: IntArray,
    ) {
        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                "draw bounds must be finite"
            }
            require(right > left && bottom > top) { "draw bounds must be non-empty" }
            require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
        }

        fun copyColor(): OpaqueRect = copy(color = color.copyOf())
    }

    private val background = background.copyOf().also(::requireRgba)
    private val draws = draws.map(OpaqueRect::copyColor)

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5f
            val py = y + 0.5f
            var color = background
            if (contains(clip, px, py)) {
                draws.forEach { draw ->
                    if (px >= draw.left && px < draw.right && py >= draw.top && py < draw.bottom) {
                        color = draw.color
                    }
                }
            }
            val offset = (y * width + x) * 4
            for (channel in 0 until 4) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun contains(shape: DeviceRRect, x: Float, y: Float): Boolean {
        if (x < shape.left || x >= shape.right || y < shape.top || y >= shape.bottom) return false
        if (shape.radiusX == 0f || shape.radiusY == 0f) return true
        val inLeft = x < shape.left + shape.radiusX
        val inRight = x >= shape.right - shape.radiusX
        val inTop = y < shape.top + shape.radiusY
        val inBottom = y >= shape.bottom - shape.radiusY
        val centerX = when {
            inLeft -> shape.left + shape.radiusX
            inRight -> shape.right - shape.radiusX
            else -> return true
        }
        val centerY = when {
            inTop -> shape.top + shape.radiusY
            inBottom -> shape.bottom - shape.radiusY
            else -> return true
        }
        val dx = (x - centerX) / shape.radiusX
        val dy = (y - centerY) / shape.radiusY
        return dx * dx + dy * dy <= 1f
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
    }
}
