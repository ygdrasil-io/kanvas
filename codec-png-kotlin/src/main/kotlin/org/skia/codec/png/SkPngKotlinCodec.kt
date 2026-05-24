package org.skia.codec.png

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * First pure-Kotlin PNG decoder slice.
 *
 * Supports the baseline non-interlaced PNG path used by small fixtures and
 * many generated assets: 8-bit grayscale (colour type 0), 8-bit RGB (colour
 * type 2), indexed colour (colour type 3, bit depths 1/2/4/8), 8-bit
 * grayscale+alpha (colour type 4), and RGBA (colour type 6), deflated IDAT
 * data, and the five standard PNG scanline filters.
 */
public class SkPngKotlinCodec private constructor(
    private val png: ParsedPng,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = png.width,
            height = png.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidConversion
        }

        val bpp = png.filterBytesPerPixel
        val rowBytes = png.rowBytes
        val expected = (rowBytes + 1) * png.height
        val inflated = try {
            inflate(png.idat, expected)
        } catch (_: DataFormatException) {
            return Result.kErrorInInput
        }
        if (inflated.size < expected) return Result.kIncompleteInput

        val previous = ByteArray(rowBytes)
        val current = ByteArray(rowBytes)
        var src = 0
        for (y in 0 until png.height) {
            val filter = inflated[src++].toInt() and 0xFF
            if (src + rowBytes > inflated.size) return Result.kIncompleteInput
            for (x in 0 until rowBytes) current[x] = inflated[src++]
            if (!unfilter(filter, current, previous, bpp)) return Result.kErrorInInput

            var p = 0
            for (x in 0 until png.width) {
                when (png.colorType) {
                    COLOR_GRAYSCALE -> {
                        val gray = if (png.bitDepth == 8) {
                            current[p++].toInt() and 0xFF
                        } else {
                            scaleSample(readPackedSample(current, x, png.bitDepth), png.bitDepth)
                        }
                        dst.setPixel(x, y, argb(0xFF, gray, gray, gray))
                    }
                    COLOR_RGB -> {
                        val r = current[p++].toInt() and 0xFF
                        val g = current[p++].toInt() and 0xFF
                        val b = current[p++].toInt() and 0xFF
                        dst.setPixel(x, y, argb(0xFF, r, g, b))
                    }
                    COLOR_PALETTE -> {
                        val index = if (png.bitDepth == 8) {
                            current[p++].toInt() and 0xFF
                        } else {
                            readPackedSample(current, x, png.bitDepth)
                        }
                        val palette = png.palette ?: return Result.kErrorInInput
                        if (index >= palette.size) return Result.kErrorInInput
                        dst.setPixel(x, y, palette[index])
                    }
                    COLOR_GRAYSCALE_ALPHA -> {
                        val gray = current[p++].toInt() and 0xFF
                        val alpha = current[p++].toInt() and 0xFF
                        dst.setPixel(x, y, argb(alpha, gray, gray, gray))
                    }
                    COLOR_RGBA -> {
                        val r = current[p++].toInt() and 0xFF
                        val g = current[p++].toInt() and 0xFF
                        val b = current[p++].toInt() and 0xFF
                        val a = current[p++].toInt() and 0xFF
                        dst.setPixel(x, y, argb(a, r, g, b))
                    }
                    else -> return Result.kErrorInInput
                }
            }

            current.copyInto(previous)
        }
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "png"

        override fun matches(data: ByteArray): Boolean = hasPngSignature(data)

        override fun make(data: ByteArray): SkCodec? {
            if (!hasPngSignature(data)) return null
            val png = parse(data) ?: return null
            return SkPngKotlinCodec(png)
        }

        private fun parse(data: ByteArray): ParsedPng? {
            var offset = PNG_SIGNATURE.size
            var header: Header? = null
            val idat = ByteArrayOutputStream()
            var palette: IntArray? = null
            var transparency: ByteArray? = null
            var sawIdat = false
            var sawIend = false

            while (offset + CHUNK_OVERHEAD <= data.size) {
                val length = readI32BE(data, offset)
                if (length < 0) return null
                val typeOffset = offset + 4
                val dataOffset = typeOffset + 4
                val crcOffset = dataOffset + length
                if (crcOffset.toLong() + 4L > data.size.toLong()) return null

                val type = readType(data, typeOffset)
                if (!crcMatches(data, typeOffset, length + 4, crcOffset)) return null
                when (type) {
                    TYPE_IHDR -> {
                        if (header != null || offset != PNG_SIGNATURE.size || length != 13) return null
                        header = parseHeader(data, dataOffset) ?: return null
                    }
                    TYPE_IDAT -> {
                        if (header == null || sawIend) return null
                        if (header.colorType == COLOR_PALETTE && palette == null) return null
                        idat.write(data, dataOffset, length)
                        sawIdat = true
                    }
                    TYPE_PLTE -> {
                        if (header == null || sawIdat || sawIend || palette != null) return null
                        palette = parsePalette(data, dataOffset, length) ?: return null
                    }
                    TYPE_TRNS -> {
                        if (header == null || sawIdat || sawIend || transparency != null) return null
                        if (header.colorType != COLOR_PALETTE || palette == null) return null
                        if (length > palette.size) return null
                        transparency = data.copyOfRange(dataOffset, dataOffset + length)
                    }
                    TYPE_IEND -> {
                        if (length != 0 || header == null) return null
                        sawIend = true
                        offset = crcOffset + 4
                        break
                    }
                    else -> {
                        if (isCritical(type)) return null
                    }
                }
                offset = crcOffset + 4
            }

            val h = header ?: return null
            if (!sawIdat || !sawIend || offset > data.size) return null
            val finalPalette = if (h.colorType == COLOR_PALETTE) {
                paletteWithTransparency(palette, transparency) ?: return null
            } else {
                null
            }
            if (finalPalette != null && finalPalette.size > (1 shl h.bitDepth)) return null
            return ParsedPng(
                width = h.width,
                height = h.height,
                bitDepth = h.bitDepth,
                colorType = h.colorType,
                rowBytes = h.rowBytes,
                filterBytesPerPixel = h.filterBytesPerPixel,
                idat = idat.toByteArray(),
                palette = finalPalette,
            )
        }

        private fun parseHeader(data: ByteArray, offset: Int): Header? {
            val width = readI32BE(data, offset)
            val height = readI32BE(data, offset + 4)
            val bitDepth = data[offset + 8].toInt() and 0xFF
            val colorType = data[offset + 9].toInt() and 0xFF
            val compression = data[offset + 10].toInt() and 0xFF
            val filter = data[offset + 11].toInt() and 0xFF
            val interlace = data[offset + 12].toInt() and 0xFF
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
            if (!isSupportedColorDepth(colorType, bitDepth)) return null
            if (compression != 0 || filter != 0 || interlace != 0) return null
            val rowBits = width.toLong() * bitsPerPixel(colorType, bitDepth).toLong()
            val rowBytes = (rowBits + 7L) / 8L
            if (rowBytes > Int.MAX_VALUE) return null
            val expected = (rowBytes + 1L) * height.toLong()
            if (expected > Int.MAX_VALUE) return null
            return Header(
                width = width,
                height = height,
                bitDepth = bitDepth,
                colorType = colorType,
                rowBytes = rowBytes.toInt(),
                filterBytesPerPixel = filterBytesPerPixel(colorType, bitDepth),
            )
        }

        private fun isSupportedColorDepth(colorType: Int, bitDepth: Int): Boolean =
            when (colorType) {
                COLOR_GRAYSCALE -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8
                COLOR_RGB -> bitDepth == 8
                COLOR_PALETTE -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8
                COLOR_GRAYSCALE_ALPHA -> bitDepth == 8
                COLOR_RGBA -> bitDepth == 8
                else -> false
            }

        private fun bitsPerPixel(colorType: Int, bitDepth: Int): Int =
            when (colorType) {
                COLOR_GRAYSCALE, COLOR_PALETTE -> bitDepth
                COLOR_RGB -> bitDepth * 3
                COLOR_GRAYSCALE_ALPHA -> bitDepth * 2
                COLOR_RGBA -> bitDepth * 4
                else -> 0
            }

        private fun filterBytesPerPixel(colorType: Int, bitDepth: Int): Int =
            when (colorType) {
                COLOR_RGB -> 3
                COLOR_GRAYSCALE_ALPHA -> 2
                COLOR_RGBA -> 4
                else -> 1
            }

        private fun parsePalette(data: ByteArray, offset: Int, length: Int): IntArray? {
            if (length == 0 || length % 3 != 0) return null
            val entries = length / 3
            if (entries > 256) return null
            return IntArray(entries) { i ->
                val p = offset + i * 3
                argb(
                    a = 0xFF,
                    r = data[p].toInt() and 0xFF,
                    g = data[p + 1].toInt() and 0xFF,
                    b = data[p + 2].toInt() and 0xFF,
                )
            }
        }

        private fun paletteWithTransparency(palette: IntArray?, transparency: ByteArray?): IntArray? {
            val colors = palette?.copyOf() ?: return null
            if (transparency != null) {
                for (i in transparency.indices) {
                    colors[i] = (colors[i] and 0x00FFFFFF) or ((transparency[i].toInt() and 0xFF) shl 24)
                }
            }
            return colors
        }

        private fun hasPngSignature(data: ByteArray): Boolean {
            if (data.size < PNG_SIGNATURE.size) return false
            for (i in PNG_SIGNATURE.indices) {
                if (data[i] != PNG_SIGNATURE[i]) return false
            }
            return true
        }

        private fun crcMatches(data: ByteArray, offset: Int, length: Int, expectedOffset: Int): Boolean {
            val crc = CRC32()
            crc.update(data, offset, length)
            return crc.value.toInt() == readI32BE(data, expectedOffset)
        }
    }

    private data class Header(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val rowBytes: Int,
        val filterBytesPerPixel: Int,
    )

    private data class ParsedPng(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val rowBytes: Int,
        val filterBytesPerPixel: Int,
        val idat: ByteArray,
        val palette: IntArray?,
    )
}

public class PngKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkPngKotlinCodec.Decoder)
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private const val CHUNK_OVERHEAD: Int = 12
private const val COLOR_GRAYSCALE: Int = 0
private const val COLOR_RGB: Int = 2
private const val COLOR_PALETTE: Int = 3
private const val COLOR_GRAYSCALE_ALPHA: Int = 4
private const val COLOR_RGBA: Int = 6
private const val MAX_DIMENSION: Int = 100_000
private const val TYPE_IHDR: Int = 0x49484452
private const val TYPE_IDAT: Int = 0x49444154
private const val TYPE_IEND: Int = 0x49454E44
private const val TYPE_PLTE: Int = 0x504C5445
private const val TYPE_TRNS: Int = 0x74524E53

private fun inflate(data: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream(expectedSize)
    val buffer = ByteArray(8192)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            when {
                count > 0 -> out.write(buffer, 0, count)
                inflater.needsInput() || inflater.needsDictionary() -> break
            }
            if (out.size() > expectedSize) break
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}

private fun unfilter(filter: Int, row: ByteArray, previous: ByteArray, bpp: Int): Boolean {
    for (i in row.indices) {
        val raw = row[i].toInt() and 0xFF
        val left = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
        val up = previous[i].toInt() and 0xFF
        val upLeft = if (i >= bpp) previous[i - bpp].toInt() and 0xFF else 0
        val predictor = when (filter) {
            0 -> 0
            1 -> left
            2 -> up
            3 -> (left + up) / 2
            4 -> paeth(left, up, upLeft)
            else -> return false
        }
        row[i] = ((raw + predictor) and 0xFF).toByte()
    }
    return true
}

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = kotlin.math.abs(p - a)
    val pb = kotlin.math.abs(p - b)
    val pc = kotlin.math.abs(p - c)
    return when {
        pa <= pb && pa <= pc -> a
        pb <= pc -> b
        else -> c
    }
}

private fun readI32BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun readType(bytes: ByteArray, offset: Int): Int = readI32BE(bytes, offset)

private fun readPackedSample(row: ByteArray, x: Int, bitDepth: Int): Int {
    val bitOffset = x * bitDepth
    val value = row[bitOffset / 8].toInt() and 0xFF
    val shift = 8 - bitDepth - (bitOffset % 8)
    return (value ushr shift) and ((1 shl bitDepth) - 1)
}

private fun scaleSample(value: Int, bitDepth: Int): Int =
    if (bitDepth == 8) value else value * 255 / ((1 shl bitDepth) - 1)

private fun isCritical(type: Int): Boolean =
    (((type ushr 24) and 0x20) == 0)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
