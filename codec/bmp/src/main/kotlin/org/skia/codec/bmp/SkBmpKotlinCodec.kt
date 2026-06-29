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
 * - BI_RGB, BI_BITFIELDS, BI_RLE8 and BI_RLE4 compression
 * - BITMAPV4HEADER/BITMAPV5HEADER RGB(A) masks; embedded ICC data is accepted but ignored
 * - indexed 1/4/8 bpp palettes and direct 16/24/32 bpp pixels
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

        if (header.compression == BI_RLE8 || header.compression == BI_RLE4) {
            return decodeRle(dst)
        } else {
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
            16 -> {
                val off = row + x * 2
                val value = readU16LE(bytes, off)
                val masks = header.bitMasks ?: DEFAULT_RGB555_MASKS
                argb(
                    0xFF,
                    scaleMasked(value, masks.red),
                    scaleMasked(value, masks.green),
                    scaleMasked(value, masks.blue),
                )
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
                val masks = header.bitMasks
                if (masks != null) {
                    val value = readI32LE(bytes, off)
                    argb(
                        masks.alpha?.let { scaleMasked(value, it) } ?: 0xFF,
                        scaleMasked(value, masks.red),
                        scaleMasked(value, masks.green),
                        scaleMasked(value, masks.blue),
                    )
                } else {
                    val b = bytes[off].toInt() and 0xFF
                    val g = bytes[off + 1].toInt() and 0xFF
                    val r = bytes[off + 2].toInt() and 0xFF
                    val a = bytes[off + 3].toInt() and 0xFF
                    argb(a, r, g, b)
                }
            }
            else -> TRANSPARENT_BLACK
        }
    }

    private fun decodeRle(dst: SkBitmap): Result {
        if (header.topDown) return Result.kInvalidInput
        if (header.pixelOffset > bytes.size) return Result.kIncompleteInput

        val background = header.palette.firstOrNull() ?: TRANSPARENT_BLACK
        for (dy in 0 until header.height) {
            for (x in 0 until header.width) {
                dst.setPixel(x, dy, background)
            }
        }

        var offset = header.pixelOffset
        var x = 0
        var fileY = 0
        while (offset < bytes.size) {
            val count = bytes[offset++].toInt() and 0xFF
            if (offset >= bytes.size) return Result.kIncompleteInput
            val value = bytes[offset++].toInt() and 0xFF
            if (count > 0) {
                if (!writeRleRun(dst, x, fileY, count, value)) return Result.kInvalidInput
                x += count
                continue
            }

            when (value) {
                RLE_EOL -> {
                    x = 0
                    fileY++
                }
                RLE_EOF -> return Result.kSuccess
                RLE_DELTA -> {
                    if (offset + 2 > bytes.size) return Result.kIncompleteInput
                    x += bytes[offset++].toInt() and 0xFF
                    fileY += bytes[offset++].toInt() and 0xFF
                    if (x > header.width || fileY > header.height) return Result.kInvalidInput
                }
                else -> {
                    if (!writeRleAbsolute(dst, x, fileY, value, offset)) return Result.kInvalidInput
                    offset += absoluteBytes(value)
                    if ((absoluteBytes(value) and 1) != 0) offset++
                    if (offset > bytes.size) return Result.kIncompleteInput
                    x += value
                }
            }
        }
        return Result.kIncompleteInput
    }

    private fun writeRleRun(dst: SkBitmap, x: Int, fileY: Int, count: Int, value: Int): Boolean {
        if (x < 0 || x + count > header.width || fileY < 0 || fileY >= header.height) return false
        for (i in 0 until count) {
            val index = if (header.compression == BI_RLE8) {
                value
            } else if ((i and 1) == 0) {
                value ushr 4
            } else {
                value and 0x0F
            }
            if (!setRlePixel(dst, x + i, fileY, index)) return false
        }
        return true
    }

    private fun writeRleAbsolute(dst: SkBitmap, x: Int, fileY: Int, count: Int, offset: Int): Boolean {
        val bytesToRead = absoluteBytes(count)
        if (offset + bytesToRead > bytes.size) return false
        if (x < 0 || x + count > header.width || fileY < 0 || fileY >= header.height) return false
        for (i in 0 until count) {
            val packed = bytes[offset + if (header.compression == BI_RLE8) i else i / 2].toInt() and 0xFF
            val index = if (header.compression == BI_RLE8) {
                packed
            } else if ((i and 1) == 0) {
                packed ushr 4
            } else {
                packed and 0x0F
            }
            if (!setRlePixel(dst, x + i, fileY, index)) return false
        }
        return true
    }

    private fun setRlePixel(dst: SkBitmap, x: Int, fileY: Int, index: Int): Boolean {
        if (index !in header.palette.indices) return false
        val dy = header.height - 1 - fileY
        dst.setPixel(x, dy, header.palette[index])
        return true
    }

    private fun absoluteBytes(count: Int): Int =
        if (header.compression == BI_RLE8) count else (count + 1) / 2

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
            if (!isSupportedCompression(compression, bitsPerPixel, topDown)) return null
            if (bitsPerPixel !in SUPPORTED_BPP) return null
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null

            val bitMasks = if (usesBitMasks(compression, dibSize, bitsPerPixel)) {
                readBitMasks(data, dibSize) ?: return null
            } else {
                null
            }

            val palette = if (bitsPerPixel <= 8) {
                val defaultEntries = 1 shl bitsPerPixel
                val entryCount = if (colorsUsed > 0) colorsUsed else defaultEntries
                if (entryCount !in 1..defaultEntries) return null
                readPalette(data, 14 + dibSize, entryCount) ?: return null
            } else {
                IntArray(0)
            }

            if (compression == BI_RGB || compression == BI_BITFIELDS) {
                val rowBytes = rowBytes(width, bitsPerPixel)
                val required = pixelOffset.toLong() + rowBytes.toLong() * height.toLong()
                if (required > data.size.toLong()) return null
            } else if (pixelOffset > data.size) {
                return null
            }

            return Header(
                width = width,
                height = height,
                topDown = topDown,
                bitsPerPixel = bitsPerPixel,
                compression = compression,
                pixelOffset = pixelOffset,
                palette = palette,
                bitMasks = bitMasks,
            )
        }

        private fun readBitMasks(data: ByteArray, dibSize: Int): BitMasks? {
            val maskOffset = 14 + 40
            if (dibSize < 52 && data.size < maskOffset + 12) return null
            if (data.size < maskOffset + 12) return null
            val masks = BitMasks(
                red = readI32LE(data, maskOffset),
                green = readI32LE(data, maskOffset + 4),
                blue = readI32LE(data, maskOffset + 8),
                alpha = if (dibSize >= 56) readI32LE(data, maskOffset + 12).takeIf { it != 0 } else null,
            )
            return if (masks.isSupportedRgb()) masks else null
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
        val compression: Int,
        val pixelOffset: Int,
        val palette: IntArray,
        val bitMasks: BitMasks?,
    )

}

private data class BitMasks(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int? = null,
) {
    fun isSupportedRgb(): Boolean =
        red != 0 && green != 0 && blue != 0 &&
            red and green == 0 &&
            red and blue == 0 &&
            green and blue == 0 &&
            (alpha == null ||
                (alpha != 0 &&
                    alpha and red == 0 &&
                    alpha and green == 0 &&
                    alpha and blue == 0))
}

public class BmpKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkBmpKotlinCodec.Decoder)
}

private const val FILE_HEADER_SIZE: Int = 14
private const val BITMAP_V4_HEADER_SIZE: Int = 108
private const val BI_RGB: Int = 0
private const val BI_RLE8: Int = 1
private const val BI_RLE4: Int = 2
private const val BI_BITFIELDS: Int = 3
private const val RLE_EOL: Int = 0
private const val RLE_EOF: Int = 1
private const val RLE_DELTA: Int = 2
private const val MAX_DIMENSION: Int = 100_000
private const val TRANSPARENT_BLACK: Int = 0
private val DEFAULT_RGB555_MASKS: BitMasks = BitMasks(
    red = 0x7C00,
    green = 0x03E0,
    blue = 0x001F,
)
private val SUPPORTED_BPP: Set<Int> = setOf(1, 4, 8, 16, 24, 32)

private fun isSupportedCompression(compression: Int, bitsPerPixel: Int, topDown: Boolean): Boolean =
    when (compression) {
        BI_RGB -> true
        BI_RLE8 -> bitsPerPixel == 8 && !topDown
        BI_RLE4 -> bitsPerPixel == 4 && !topDown
        BI_BITFIELDS -> bitsPerPixel == 16 || bitsPerPixel == 32
        else -> false
    }

private fun usesBitMasks(compression: Int, dibSize: Int, bitsPerPixel: Int): Boolean =
    (compression == BI_BITFIELDS || dibSize >= BITMAP_V4_HEADER_SIZE) && (bitsPerPixel == 16 || bitsPerPixel == 32)

private fun scaleMasked(pixel: Int, mask: Int): Int {
    val shift = Integer.numberOfTrailingZeros(mask)
    val max = mask ushr shift
    val value = (pixel and mask) ushr shift
    return (value * 255 + max / 2) / max
}

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
