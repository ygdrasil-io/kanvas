package org.graphiks.kanvas.image

import org.graphiks.kanvas.types.ColorSpace

enum class ColorType { RGBA_8888, BGRA_8888, ALPHA_8, GRAY_8 }

data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val sourceId: String,
    val pixels: ByteArray? = null,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): Image =
            Image(0, 0, ColorType.RGBA_8888, "decode-placeholder:${bytes.size}")

        fun fromPixels(
            width: Int,
            height: Int,
            pixels: ByteArray,
            colorType: ColorType = ColorType.RGBA_8888,
            sourceId: String = "pixels",
        ): Image = Image(width, height, colorType, sourceId, pixels)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Image) return false
        return width == other.width && height == other.height &&
            colorType == other.colorType && sourceId == other.sourceId
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + colorType.hashCode()
        result = 31 * result + sourceId.hashCode()
        return result
    }
}
