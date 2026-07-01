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

private fun unfilter(curr: ByteArray, prev: ByteArray, filterType: Int, bpp: Int) {
    when (filterType) {
        0 -> {}
        1 -> for (i in 0 until curr.size) {
            val left = if (i >= bpp) (curr[i - bpp].toInt() and 0xFF) else 0
            curr[i] = ((curr[i].toInt() and 0xFF) + left).toByte()
        }
        2 -> for (i in 0 until curr.size) {
            curr[i] = ((curr[i].toInt() and 0xFF) + (prev[i].toInt() and 0xFF)).toByte()
        }
        3 -> for (i in 0 until curr.size) {
            val left = if (i >= bpp) (curr[i - bpp].toInt() and 0xFF) else 0
            curr[i] = ((curr[i].toInt() and 0xFF) + ((left + (prev[i].toInt() and 0xFF)) / 2)).toByte()
        }
        4 -> for (i in 0 until curr.size) {
            val left = if (i >= bpp) (curr[i - bpp].toInt() and 0xFF) else 0
            val above = prev[i].toInt() and 0xFF
            val aboveLeft = if (i >= bpp) (prev[i - bpp].toInt() and 0xFF) else 0
            val p = left + above - aboveLeft
            val pa = kotlin.math.abs(p - left)
            val pb = kotlin.math.abs(p - above)
            val pc = kotlin.math.abs(p - aboveLeft)
            val pr = if (pa <= pb && pa <= pc) left else if (pb <= pc) above else aboveLeft
            curr[i] = ((curr[i].toInt() and 0xFF) + pr).toByte()
        }
    }
}

private fun decodePng(bytes: ByteArray): Image {
    try {
        val sig = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size < sig.size) return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")
        for (i in sig.indices) {
            if (bytes[i] != sig[i]) return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")
        }

        var off = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idatChunks = mutableListOf<ByteArray>()

        while (off + 8 <= bytes.size) {
            val len = ((bytes[off].toInt() and 0xFF) shl 24) or
                ((bytes[off + 1].toInt() and 0xFF) shl 16) or
                ((bytes[off + 2].toInt() and 0xFF) shl 8) or
                (bytes[off + 3].toInt() and 0xFF)
            val type = String(byteArrayOf(bytes[off + 4], bytes[off + 5], bytes[off + 6], bytes[off + 7]))
            val dataOff = off + 8
            if (dataOff + len > bytes.size) break
            val data = bytes.copyOfRange(dataOff, dataOff + len)

            when (type) {
                "IHDR" -> {
                    if (data.size >= 8) {
                        width = ((data[0].toInt() and 0xFF) shl 24) or
                            ((data[1].toInt() and 0xFF) shl 16) or
                            ((data[2].toInt() and 0xFF) shl 8) or
                            (data[3].toInt() and 0xFF)
                        height = ((data[4].toInt() and 0xFF) shl 24) or
                            ((data[5].toInt() and 0xFF) shl 16) or
                            ((data[6].toInt() and 0xFF) shl 8) or
                            (data[7].toInt() and 0xFF)
                        bitDepth = data[8].toInt() and 0xFF
                        colorType = data[9].toInt() and 0xFF
                    }
                }
                "IDAT" -> idatChunks.add(data)
                "IEND" -> break
            }

            off = dataOff + len + 4
        }

        if (width <= 0 || height <= 0) return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")

        if (colorType !in intArrayOf(2, 6) || bitDepth != 8) {
            return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")
        }

        val bpp = if (colorType == 2) 3 else 4
        val stride = width * bpp

        val compressed = ByteArray(idatChunks.sumOf { it.size })
        var cOff = 0
        for (c in idatChunks) {
            System.arraycopy(c, 0, compressed, cOff, c.size)
            cOff += c.size
        }

        val inflater = java.util.zip.Inflater()
        inflater.setInput(compressed)
        val raw = ByteArray(width * height * 4 + height)
        val rawLen = inflater.inflate(raw)
        inflater.end()

        val pixels = ByteArray(width * height * 4)
        val prev = ByteArray(stride)
        val curr = ByteArray(stride)

        for (y in 0 until height) {
            if (y * (stride + 1) + 1 > rawLen) break
            val filterType = raw[y * (stride + 1)].toInt() and 0xFF
            val rowStart = y * (stride + 1) + 1
            System.arraycopy(raw, rowStart, curr, 0, stride)
            unfilter(curr, prev, filterType, bpp)

            for (x in 0 until width) {
                val px = y * width + x
                val cx = x * bpp
                pixels[px * 4] = curr[cx]
                pixels[px * 4 + 1] = curr[cx + 1]
                pixels[px * 4 + 2] = curr[cx + 2]
                pixels[px * 4 + 3] = if (colorType == 6) curr[cx + 3] else 255.toByte()
            }

            System.arraycopy(curr, 0, prev, 0, stride)
        }

        return Image.fromPixels(width, height, pixels, ColorType.RGBA_8888, "decode-png:${bytes.size}")
    } catch (_: Exception) {
        return Image(0, 0, ColorType.RGBA_8888, "decode-png:${bytes.size}")
    }
}
