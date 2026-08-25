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

    data class CornerRadii(val x: Float, val y: Float) {
        init {
            require(x.isFinite() && y.isFinite() && x >= 0f && y >= 0f) {
                "RRect radii must be finite and non-negative"
            }
        }
    }

    data class DeviceRRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val topLeft: CornerRadii,
        val topRight: CornerRadii = topLeft,
        val bottomRight: CornerRadii = topRight,
        val bottomLeft: CornerRadii = topLeft,
    ) {
        constructor(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            radiusX: Float,
            radiusY: Float,
        ) : this(left, top, right, bottom, CornerRadii(radiusX, radiusY))

        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                "RRect bounds must be finite"
            }
            require(right > left && bottom > top) { "RRect bounds must be non-empty" }
            require(topLeft.x + topRight.x <= right - left)
            require(bottomLeft.x + bottomRight.x <= right - left)
            require(topLeft.y + bottomLeft.y <= bottom - top)
            require(topRight.y + bottomRight.y <= bottom - top)
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
            shape.topLeft.x > 0f && shape.topLeft.y > 0f &&
                x < shape.left + shape.topLeft.x && y < shape.top + shape.topLeft.y ->
                shape.topLeft to floatArrayOf(shape.left + shape.topLeft.x, shape.top + shape.topLeft.y)
            shape.topRight.x > 0f && shape.topRight.y > 0f &&
                x >= shape.right - shape.topRight.x && y < shape.top + shape.topRight.y ->
                shape.topRight to floatArrayOf(shape.right - shape.topRight.x, shape.top + shape.topRight.y)
            shape.bottomLeft.x > 0f && shape.bottomLeft.y > 0f &&
                x < shape.left + shape.bottomLeft.x && y >= shape.bottom - shape.bottomLeft.y ->
                shape.bottomLeft to floatArrayOf(shape.left + shape.bottomLeft.x, shape.bottom - shape.bottomLeft.y)
            shape.bottomRight.x > 0f && shape.bottomRight.y > 0f &&
                x >= shape.right - shape.bottomRight.x && y >= shape.bottom - shape.bottomRight.y ->
                shape.bottomRight to floatArrayOf(shape.right - shape.bottomRight.x, shape.bottom - shape.bottomRight.y)
            else -> null
        } ?: return true
        val (radii, center) = corner
        val dx = (x - center[0]) / radii.x
        val dy = (y - center[1]) / radii.y
        return dx * dx + dy * dy <= 1f
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
    }
}
