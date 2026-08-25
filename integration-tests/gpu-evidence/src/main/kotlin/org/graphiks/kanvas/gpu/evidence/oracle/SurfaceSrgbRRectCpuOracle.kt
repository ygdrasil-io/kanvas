package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent analytic pixel-center oracle for opaque device-space RRects and DRRects. */
class SurfaceSrgbRRectCpuOracle(
    background: IntArray,
    fill: IntArray,
    val outer: DeviceRRect,
    inner: DeviceRRect? = null,
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
            require(radiusX <= (right - left) / 2f && radiusY <= (bottom - top) / 2f) {
                "RRect radii must fit within bounds"
            }
        }
    }

    private val background = background.copyOf().also(::requireRgba)
    private val fill = fill.copyOf().also(::requireRgba)
    private val inner = inner?.also {
        require(it.left >= outer.left && it.top >= outer.top && it.right <= outer.right && it.bottom <= outer.bottom) {
            "inner RRect must be contained by outer RRect"
        }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val deviceX = x + 0.5f
            val deviceY = y + 0.5f
            val covered = contains(outer, deviceX, deviceY) && (inner == null || !contains(inner, deviceX, deviceY))
            val color = if (covered) fill else background
            val offset = (y * width + x) * 4
            for (channel in 0 until 4) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun contains(shape: DeviceRRect, x: Float, y: Float): Boolean {
        if (x < shape.left || x >= shape.right || y < shape.top || y >= shape.bottom) return false
        val corner = when {
            shape.radiusX > 0f && shape.radiusY > 0f && x < shape.left + shape.radiusX && y < shape.top + shape.radiusY ->
                floatArrayOf(shape.left + shape.radiusX, shape.top + shape.radiusY)
            shape.radiusX > 0f && shape.radiusY > 0f && x >= shape.right - shape.radiusX && y < shape.top + shape.radiusY ->
                floatArrayOf(shape.right - shape.radiusX, shape.top + shape.radiusY)
            shape.radiusX > 0f && shape.radiusY > 0f && x < shape.left + shape.radiusX && y >= shape.bottom - shape.radiusY ->
                floatArrayOf(shape.left + shape.radiusX, shape.bottom - shape.radiusY)
            shape.radiusX > 0f && shape.radiusY > 0f && x >= shape.right - shape.radiusX && y >= shape.bottom - shape.radiusY ->
                floatArrayOf(shape.right - shape.radiusX, shape.bottom - shape.radiusY)
            else -> null
        } ?: return true
        val dx = (x - corner[0]) / shape.radiusX
        val dy = (y - corner[1]) / shape.radiusY
        return dx * dx + dy * dy <= 1f
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
    }
}
