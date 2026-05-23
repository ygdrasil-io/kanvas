package org.skia.codec.bmp

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

/**
 * Pure Kotlin BMP decoder for the common uncompressed Windows BMP path.
 *
 * Supported in this first backend slice:
 * - Windows/OS2 file signature `BM`
 * - DIB headers with at least the BITMAPINFOHEADER fields
 * - BI_RGB compression
 * - indexed 1/4/8 bpp palettes and direct 24/32 bpp BGRA pixels
 * - bottom-up and top-down row order
 */
public class SkBmpKotlinCodec private constructor(
    private val bytes: ByteArray,
    private val header: Header,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = header.width,
            height = header.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kBMP

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

        val rowBytes = rowBytes(header.width, header.bitsPerPixel)
        val required = header.pixelOffset.toLong() + rowBytes.toLong() * header.height.toLong()
        if (required > bytes.size.toLong()) return Result.kIncompleteInput

        for (dy in 0 until header.height) {
            val sy = if (header.topDown) dy else header.height - 1 - dy
            val row = header.pixelOffset + sy * rowBytes
            for (x in 0 until header.width) {
                dst.setPixel(x, dy, readPixel(row, x))
            }
        }
        return Result.kSuccess
    }

    private fun readPixel(row: Int, x: Int): Int {
        return when (header.bitsPerPixel) {
            1 -> {
                val packed = bytes[row + x / 8].toInt() and 0xFF
                val index = (packed ushr (7 - (x and 7))) and 0x01
                header.palette[index]
            }
            4 -> {
                val packed = bytes[row + x / 2].toInt() and 0xFF
                val index = if ((x and 1) == 0) packed ushr 4 else packed and 0x0F
                header.palette[index]
            }
            8 -> {
                val index = bytes[row + x].toInt() and 0xFF
                header.palette[index]
            }
            24 -> {
                val off = row + x * 3
                val b = bytes[off].toInt() and 0xFF
                val g = bytes[off + 1].toInt() and 0xFF
                val r = bytes[off + 2].toInt() and 0xFF
                argb(0xFF, r, g, b)
            }
            32 -> {
                val off = row + x * 4
                val b = bytes[off].toInt() and 0xFF
                val g = bytes[off + 1].toInt() and 0xFF
                val r = bytes[off + 2].toInt() and 0xFF
                val a = bytes[off + 3].toInt() and 0xFF
                argb(a, r, g, b)
            }
            else -> TRANSPARENT_BLACK
        }
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "bmp"

        override fun matches(data: ByteArray): Boolean =
            data.size >= FILE_HEADER_SIZE &&
                data[0] == 'B'.code.toByte() &&
                data[1] == 'M'.code.toByte()

        override fun make(data: ByteArray): SkCodec? {
            if (!matches(data)) return null
            val header = parseHeader(data) ?: return null
            return SkBmpKotlinCodec(data, header)
        }

        private fun parseHeader(data: ByteArray): Header? {
            if (data.size < FILE_HEADER_SIZE + 40) return null
            val pixelOffset = readI32LE(data, 10)
            val dibSize = readI32LE(data, 14)
            if (pixelOffset < FILE_HEADER_SIZE || dibSize < 40) return null
            if (14L + dibSize.toLong() > data.size.toLong()) return null

            val width = readI32LE(data, 18)
            val rawHeight = readI32LE(data, 22)
            if (width <= 0 || rawHeight == 0) return null
            val height = kotlin.math.abs(rawHeight)
            val topDown = rawHeight < 0
            val planes = readU16LE(data, 26)
            val bitsPerPixel = readU16LE(data, 28)
            val compression = readI32LE(data, 30)
            val colorsUsed = readI32LE(data, 46)

            if (planes != 1) return null
            if (compression != BI_RGB) return null
            if (bitsPerPixel !in SUPPORTED_BPP) return null
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null

            val palette = if (bitsPerPixel <= 8) {
                val defaultEntries = 1 shl bitsPerPixel
                val entryCount = if (colorsUsed > 0) colorsUsed else defaultEntries
                if (entryCount !in 1..defaultEntries) return null
                readPalette(data, 14 + dibSize, entryCount) ?: return null
            } else {
                IntArray(0)
            }

            val rowBytes = rowBytes(width, bitsPerPixel)
            val required = pixelOffset.toLong() + rowBytes.toLong() * height.toLong()
            if (required > data.size.toLong()) return null

            return Header(
                width = width,
                height = height,
                topDown = topDown,
                bitsPerPixel = bitsPerPixel,
                pixelOffset = pixelOffset,
                palette = palette,
            )
        }

        private fun readPalette(data: ByteArray, offset: Int, entryCount: Int): IntArray? {
            if (offset < 0 || offset.toLong() + entryCount.toLong() * 4L > data.size.toLong()) {
                return null
            }
            val palette = IntArray(entryCount)
            for (i in 0 until entryCount) {
                val off = offset + i * 4
                val b = data[off].toInt() and 0xFF
                val g = data[off + 1].toInt() and 0xFF
                val r = data[off + 2].toInt() and 0xFF
                palette[i] = argb(0xFF, r, g, b)
            }
            return palette
        }
    }

    private data class Header(
        val width: Int,
        val height: Int,
        val topDown: Boolean,
        val bitsPerPixel: Int,
        val pixelOffset: Int,
        val palette: IntArray,
    )
}

public class BmpKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkBmpKotlinCodec.Decoder)
}

private const val FILE_HEADER_SIZE: Int = 14
private const val BI_RGB: Int = 0
private const val MAX_DIMENSION: Int = 100_000
private const val TRANSPARENT_BLACK: Int = 0
private val SUPPORTED_BPP: Set<Int> = setOf(1, 4, 8, 24, 32)

private fun rowBytes(width: Int, bitsPerPixel: Int): Int =
    ((((width.toLong() * bitsPerPixel.toLong()) + 31L) / 32L) * 4L).toInt()

private fun readU16LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

private fun readI32LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        (bytes[offset + 3].toInt() shl 24)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
