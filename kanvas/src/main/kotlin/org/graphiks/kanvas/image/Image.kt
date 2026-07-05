package org.graphiks.kanvas.image

import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.ColorSpace

enum class ColorType(val bytesPerPixel: Int) {
    RGBA_8888(4),
    BGRA_8888(4),
    ALPHA_8(1),
    GRAY_8(1),
    RGBA_F16(8),
    RGB_565(2),
    ARGB_4444(2),
}

data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val sourceId: String,
    val pixels: ByteArray? = null,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): Image {
            when (val result = ImageDecoderRegistry.decode(bytes, mimeType)) {
                is ImageDecodeResult.Success -> return result.image
                is ImageDecodeResult.Failure -> {
                    val format = detectFormatFromMagicBytes(bytes) ?: "unknown"
                    return Image(0, 0, ColorType.RGBA_8888, "decode-${format}:${bytes.size}")
                }
            }
        }

        fun fromPixels(
            width: Int,
            height: Int,
            pixels: ByteArray,
            colorType: ColorType = ColorType.RGBA_8888,
            sourceId: String = "pixels",
        ): Image = Image(width, height, colorType, sourceId, pixels)

        fun placeholder(width: Int, height: Int): Image =
            Image(width, height, ColorType.RGBA_8888, "placeholder:${width}x${height}")
    }

    fun makeShader(
        tileModeX: TileMode = TileMode.CLAMP,
        tileModeY: TileMode = TileMode.CLAMP,
        sampling: SamplingOptions = SamplingOptions.NEAREST,
    ): Shader.Image = Shader.Image(this, tileModeX, tileModeY, sampling)

    /**
     * Return a new [Image] with the same pixel data but a different
     * [ColorSpace] tag. No pixel conversion is performed — this is a
     * metadata-only operation (equivalent to Skia's
     * `SkImage::reinterpretColorSpace`).
     */
    fun reinterpretColorSpace(newColorSpace: ColorSpace): Image =
        Image(width, height, colorType, sourceId, pixels, newColorSpace)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Image) return false
        return width == other.width && height == other.height &&
            colorType == other.colorType && colorSpace == other.colorSpace &&
            sourceId == other.sourceId
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + colorType.hashCode()
        result = 31 * result + colorSpace.hashCode()
        result = 31 * result + sourceId.hashCode()
        return result
    }
}

private fun detectFormatFromMagicBytes(bytes: ByteArray): String? {
    if (bytes.size < 4) return null
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "png"
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "jpeg"
    if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return "webp"
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return "gif"
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
    return null
}
