package org.graphiks.kanvas.image

import org.graphiks.kanvas.surface.ImageEncoder
import org.graphiks.kanvas.surface.ImageEncoderRegistry
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
        fun decode(bytes: ByteArray, mimeType: String? = null): Image {
            val format = mimeType?.substringAfter("image/")?.lowercase()
                ?: detectFormatFromMagicBytes(bytes)
            if (format != null) {
                val encoder = ImageEncoderRegistry.find("decode-$format")
                if (encoder != null) {
                    val metadata = ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, ColorSpace.SRGB)
                    if (format == "png") {
                        return decodePng(bytes)
                    }
                }
                return Image(0, 0, ColorType.RGBA_8888, "decode-${format}:${bytes.size}")
            }
            return Image(0, 0, ColorType.RGBA_8888, "decode-unknown:${bytes.size}")
        }

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
    // RIFF container with "WEBP" at offset 8-11
    if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return "webp"
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return "gif"
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
    return null
}

private fun decodePng(bytes: ByteArray): Image {
    return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")
}
