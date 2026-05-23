package org.skia.codec.bmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat

class SkBmpKotlinCodecTest {

    @Test
    fun `rejects non-BMP and unsupported BMP bytes`() {
        assertNull(SkBmpKotlinCodec.Decoder.make(ByteArray(0)))
        assertNull(SkBmpKotlinCodec.Decoder.make("not-a-bmp".toByteArray()))
        assertNull(SkBmpKotlinCodec.Decoder.make(byteArrayOf(0x42, 0x00, 0x00, 0x00)))

        val rleCompressed = bmp(
            width = 1,
            height = 1,
            bitsPerPixel = 24,
            compression = 1,
            rowsTopDown = listOf(listOf(RED)),
        )
        assertNull(SkBmpKotlinCodec.Decoder.make(rleCompressed))
    }

    @Test
    fun `decodes bottom-up 24-bit RGB pixels`() {
        val codec = SkBmpKotlinCodec.Decoder.make(
            bmp(
                width = 3,
                height = 2,
                bitsPerPixel = 24,
                rowsTopDown = listOf(
                    listOf(RED, GREEN, BLUE),
                    listOf(WHITE, BLACK, YELLOW),
                ),
            ),
        )

        assertNotNull(codec)
        assertTrue(codec is SkBmpKotlinCodec)
        assertEquals(SkEncodedImageFormat.kBMP, codec!!.getEncodedFormat())
        assertEquals(3, codec.dimensions().width)
        assertEquals(2, codec.dimensions().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
        assertEquals(BLUE, bitmap.getPixel(2, 0))
        assertEquals(WHITE, bitmap.getPixel(0, 1))
        assertEquals(BLACK, bitmap.getPixel(1, 1))
        assertEquals(YELLOW, bitmap.getPixel(2, 1))
    }

    @Test
    fun `decodes top-down 32-bit BGRA pixels with alpha`() {
        val semi = argb(0x80, 0x11, 0x22, 0x33)
        val codec = SkBmpKotlinCodec.Decoder.make(
            bmp(
                width = 2,
                height = 2,
                bitsPerPixel = 32,
                topDown = true,
                rowsTopDown = listOf(
                    listOf(semi, BLUE),
                    listOf(GREEN, RED),
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(semi, bitmap!!.getPixel(0, 0))
        assertEquals(BLUE, bitmap.getPixel(1, 0))
        assertEquals(GREEN, bitmap.getPixel(0, 1))
        assertEquals(RED, bitmap.getPixel(1, 1))
    }

    @Test
    fun `decodes indexed palette BMP variants`() {
        assertPaletteDecode(bitsPerPixel = 8)
        assertPaletteDecode(bitsPerPixel = 4)
        assertPaletteDecode(bitsPerPixel = 1)
    }

    @Test
    fun `reports 8888 sRGB unpremul info`() {
        val codec = SkBmpKotlinCodec.Decoder.make(
            bmp(width = 1, height = 1, bitsPerPixel = 24, rowsTopDown = listOf(listOf(RED))),
        )!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB())
    }

    private fun assertPaletteDecode(bitsPerPixel: Int) {
        val colors = intArrayOf(BLACK, RED, GREEN, BLUE, WHITE, YELLOW, CYAN, MAGENTA)
        val rows = when (bitsPerPixel) {
            1 -> listOf(listOf(0, 1, 1, 0))
            4 -> listOf(listOf(1, 2, 3, 4))
            8 -> listOf(listOf(4, 3, 2, 1))
            else -> error("unexpected bpp")
        }
        val palette = colors.copyOf(1 shl bitsPerPixel)
        val codec = SkBmpKotlinCodec.Decoder.make(
            indexedBmp(
                width = rows[0].size,
                height = rows.size,
                bitsPerPixel = bitsPerPixel,
                palette = palette,
                indexRowsTopDown = rows,
            ),
        )!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (x in rows[0].indices) {
            assertEquals(palette[rows[0][x]], bitmap!!.getPixel(x, 0), "bpp=$bitsPerPixel x=$x")
        }
    }

    private fun bmp(
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        compression: Int = 0,
        topDown: Boolean = false,
        rowsTopDown: List<List<Int>>,
    ): ByteArray {
        val rowBytes = rowBytes(width, bitsPerPixel)
        val pixelOffset = 14 + 40
        val out = ByteArray(pixelOffset + rowBytes * height)
        writeFileAndInfoHeader(out, width, height, bitsPerPixel, compression, pixelOffset, topDown)
        for (dy in 0 until height) {
            val fileRow = if (topDown) dy else height - 1 - dy
            val row = pixelOffset + fileRow * rowBytes
            for (x in 0 until width) {
                val c = rowsTopDown[dy][x]
                when (bitsPerPixel) {
                    24 -> {
                        val off = row + x * 3
                        out[off] = b(c).toByte()
                        out[off + 1] = g(c).toByte()
                        out[off + 2] = r(c).toByte()
                    }
                    32 -> {
                        val off = row + x * 4
                        out[off] = b(c).toByte()
                        out[off + 1] = g(c).toByte()
                        out[off + 2] = r(c).toByte()
                        out[off + 3] = a(c).toByte()
                    }
                }
            }
        }
        return out
    }

    private fun indexedBmp(
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        palette: IntArray,
        indexRowsTopDown: List<List<Int>>,
    ): ByteArray {
        val rowBytes = rowBytes(width, bitsPerPixel)
        val paletteBytes = palette.size * 4
        val pixelOffset = 14 + 40 + paletteBytes
        val out = ByteArray(pixelOffset + rowBytes * height)
        writeFileAndInfoHeader(out, width, height, bitsPerPixel, 0, pixelOffset, topDown = false)
        writeI32LE(out, 46, palette.size)
        for (i in palette.indices) {
            val c = palette[i]
            val off = 14 + 40 + i * 4
            out[off] = b(c).toByte()
            out[off + 1] = g(c).toByte()
            out[off + 2] = r(c).toByte()
        }
        for (dy in 0 until height) {
            val fileRow = height - 1 - dy
            val row = pixelOffset + fileRow * rowBytes
            when (bitsPerPixel) {
                1 -> {
                    for (x in 0 until width) {
                        out[row + x / 8] = (out[row + x / 8].toInt() or
                            ((indexRowsTopDown[dy][x] and 1) shl (7 - (x and 7)))).toByte()
                    }
                }
                4 -> {
                    for (x in 0 until width) {
                        val shift = if ((x and 1) == 0) 4 else 0
                        out[row + x / 2] = (out[row + x / 2].toInt() or
                            ((indexRowsTopDown[dy][x] and 0x0F) shl shift)).toByte()
                    }
                }
                8 -> {
                    for (x in 0 until width) out[row + x] = indexRowsTopDown[dy][x].toByte()
                }
            }
        }
        return out
    }

    private fun writeFileAndInfoHeader(
        out: ByteArray,
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        compression: Int,
        pixelOffset: Int,
        topDown: Boolean,
    ) {
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeI32LE(out, 2, out.size)
        writeI32LE(out, 10, pixelOffset)
        writeI32LE(out, 14, 40)
        writeI32LE(out, 18, width)
        writeI32LE(out, 22, if (topDown) -height else height)
        writeU16LE(out, 26, 1)
        writeU16LE(out, 28, bitsPerPixel)
        writeI32LE(out, 30, compression)
    }

    private fun rowBytes(width: Int, bitsPerPixel: Int): Int =
        ((((width.toLong() * bitsPerPixel.toLong()) + 31L) / 32L) * 4L).toInt()

    private fun writeU16LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = value.toByte()
        out[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeI32LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = value.toByte()
        out[offset + 1] = (value ushr 8).toByte()
        out[offset + 2] = (value ushr 16).toByte()
        out[offset + 3] = (value ushr 24).toByte()
    }

    private fun a(c: Int): Int = (c ushr 24) and 0xFF
    private fun r(c: Int): Int = (c ushr 16) and 0xFF
    private fun g(c: Int): Int = (c ushr 8) and 0xFF
    private fun b(c: Int): Int = c and 0xFF
}

private const val BLACK: Int = -0x1000000
private const val WHITE: Int = -0x1
private const val RED: Int = -0x10000
private const val GREEN: Int = -0xff0100
private const val BLUE: Int = -0xffff01
private const val YELLOW: Int = -0x100
private const val CYAN: Int = -0xff0001
private const val MAGENTA: Int = -0xff01

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
