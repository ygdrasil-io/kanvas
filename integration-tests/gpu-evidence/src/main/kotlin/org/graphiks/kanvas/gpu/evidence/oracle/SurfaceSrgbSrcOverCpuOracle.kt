package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent straight-sRGB rectangle model for the translucent Surface fixture. */
class SurfaceSrgbSrcOverCpuOracle(
    background: IntArray,
    rectangles: List<StraightSrgbRectangle>,
) : CpuOracle {
    companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
    }

    data class StraightSrgbRectangle(val bounds: SurfaceSrgbOracleMath.PixelRect, val rgba: IntArray) {
        init {
            require(rgba.size == 4) { "RGBA color must have four channels" }
            require(rgba.all { it in 0..255 }) { "RGBA channels must be bytes" }
        }
    }

    private val background = background.copyOf().also {
        require(it.size == 4 && it.all { channel -> channel in 0..255 }) { "background must be RGBA bytes" }
    }
    private val rectangles = rectangles.map { it.copy(rgba = it.rgba.copyOf()) }
    private val backgroundLinear = SurfaceSrgbOracleMath.decodeStraight(background)
    private val rectanglesLinear = rectangles.map { SurfaceSrgbOracleMath.decodeStraight(it.rgba) }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            var color = backgroundLinear
            rectangles.forEachIndexed { index, rectangle ->
                if (rectangle.bounds.contains(x, y)) color = SurfaceSrgbOracleMath.srcOver(color, rectanglesLinear[index])
            }
            val offset = (y * width + x) * 4
            SurfaceSrgbOracleMath.storeSrgb(color).forEachIndexed { channel, value -> output[offset + channel] = value.toByte() }
        }
        return output
    }
}
