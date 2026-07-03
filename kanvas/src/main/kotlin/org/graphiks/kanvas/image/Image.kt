package org.graphiks.kanvas.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.ColorSpace
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
            val codec = Codec.MakeFromData(bytes)
            if (codec != null) {
                val info = codec.getInfo()
                val (bitmap, result) = codec.getImage()
                if (bitmap != null && result == Codec.Result.kSuccess) {
                    val w = bitmap.width; val h = bitmap.height
                    val rgba = IntArrayToRGBA(bitmap.pixels8888, w, h)
                    val format = detectFormatFromMagicBytes(bytes) ?: "image"
                    return Image(w, h, ColorType.RGBA_8888, "decode-${format}:${bytes.size}", rgba)
                }
                // Decode failed — return placeholder with format info
                val format = detectFormatFromMagicBytes(bytes) ?: "unknown"
                return Image(0, 0, ColorType.RGBA_8888, "decode-${format}:${bytes.size}")
            }
            // No codec matched
            val format = detectFormatFromMagicBytes(bytes) ?: "unknown"
            return Image(0, 0, ColorType.RGBA_8888, "decode-${format}:${bytes.size}")
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

/** Convert packed ARGB IntArray to RGBA ByteArray. */
private fun IntArrayToRGBA(argb: IntArray, width: Int, height: Int): ByteArray {
    val rgba = ByteArray(width * height * 4)
    val buf = ByteBuffer.wrap(rgba).order(ByteOrder.LITTLE_ENDIAN)
    for (pixel in argb) {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        buf.putInt((a shl 24) or (b shl 16) or (g shl 8) or r) // ARGB → ABGR
    }
    return rgba
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
