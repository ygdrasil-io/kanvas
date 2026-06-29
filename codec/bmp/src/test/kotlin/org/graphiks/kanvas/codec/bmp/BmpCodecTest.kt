package org.graphiks.kanvas.codec.bmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat

class BmpCodecTest {

    @Test
    fun `rejects non-BMP and unsupported BMP bytes`() {
        assertNull(BmpCodec.Decoder.make(ByteArray(0)))
        assertNull(BmpCodec.Decoder.make("not-a-bmp".toByteArray()))
        assertNull(BmpCodec.Decoder.make(byteArrayOf(0x42, 0x00, 0x00, 0x00)))

        val invalidRleCompressed = bmp(
            width = 1,
            height = 1,
            bitsPerPixel = 24,
            compression = 1,
            rowsTopDown = listOf(listOf(RED)),
        )
        assertNull(BmpCodec.Decoder.make(invalidRleCompressed))
    }

    @Test
    fun `decodes bottom-up 24-bit RGB pixels`() {
        val codec = BmpCodec.Decoder.make(
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
        assertTrue(codec is BmpCodec)
        assertEquals(SkEncodedImageFormat.kBMP, codec!!.getEncodedFormat())
        assertEquals(3, codec.dimensions().width)
        assertEquals(2, codec.dimensions().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
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
        val codec = BmpCodec.Decoder.make(
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
        assertEquals(Codec.Result.kSuccess, result)
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
    fun `decodes 16-bit 565 bitfields BMP pixels`() {
        val codec = BmpCodec.Decoder.make(
            bitfieldsBmp(
                width = 3,
                height = 2,
                redMask = 0xF800,
                greenMask = 0x07E0,
                blueMask = 0x001F,
                rowsTopDown = listOf(
                    listOf(RED, GREEN, BLUE),
                    listOf(WHITE, BLACK, YELLOW),
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
        assertEquals(BLUE, bitmap.getPixel(2, 0))
        assertEquals(WHITE, bitmap.getPixel(0, 1))
        assertEquals(BLACK, bitmap.getPixel(1, 1))
        assertEquals(YELLOW, bitmap.getPixel(2, 1))
    }

    @Test
    fun `decodes 16-bit 555 bitfields BMP pixels`() {
        val codec = BmpCodec.Decoder.make(
            bitfieldsBmp(
                width = 2,
                height = 2,
                redMask = 0x7C00,
                greenMask = 0x03E0,
                blueMask = 0x001F,
                topDown = true,
                rowsTopDown = listOf(
                    listOf(RED, GREEN),
                    listOf(BLUE, WHITE),
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
        assertEquals(BLUE, bitmap.getPixel(0, 1))
        assertEquals(WHITE, bitmap.getPixel(1, 1))
    }

    @Test
    fun `decodes V4 32-bit bit masks with alpha`() {
        val translucent = argb(0x44, 0x11, 0x22, 0x33)
        val codec = BmpCodec.Decoder.make(
            v4BitfieldsBmp(
                width = 2,
                height = 2,
                redMask = 0x000000FF,
                greenMask = 0x0000FF00,
                blueMask = 0x00FF0000,
                alphaMask = -0x1000000,
                rowsTopDown = listOf(
                    listOf(translucent, GREEN),
                    listOf(BLUE, argb(0x7F, 0xFE, 0xDC, 0xBA)),
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(translucent, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
        assertEquals(BLUE, bitmap.getPixel(0, 1))
        assertEquals(argb(0x7F, 0xFE, 0xDC, 0xBA), bitmap.getPixel(1, 1))
    }

    @Test
    fun `V5 BMP with embedded ICC profile exposes it via getICCProfile`() {
        val iccBytes = org.skia.foundation.SkICC.WriteToICC(
            org.skia.foundation.skcms.SkNamedTransferFn.kSRGB,
            org.skia.foundation.skcms.SkNamedGamut.kSRGB,
        )
        val codec = BmpCodec.Decoder.make(
            v4BitfieldsBmp(
                width = 1,
                height = 1,
                headerSize = 124,
                redMask = 0x00FF0000,
                greenMask = 0x0000FF00,
                blueMask = 0x000000FF,
                alphaMask = -0x1000000,
                iccProfile = iccBytes,
                rowsTopDown = listOf(listOf(argb(0xFF, 0x12, 0x34, 0x56))),
            ),
        )!!
        val profile = codec.getICCProfile()
        assertNotNull(profile, "V5 BMP with embedded ICC must expose a profile")
        assertEquals(iccBytes.size, profile!!.size)
    }

    @Test
    fun `decodes V5 32-bit bit masks with non-parseable ICC profile returns null`() {
        val codec = BmpCodec.Decoder.make(
            v4BitfieldsBmp(
                width = 1,
                height = 1,
                headerSize = 124,
                redMask = 0x00FF0000,
                greenMask = 0x0000FF00,
                blueMask = 0x000000FF,
                alphaMask = -0x1000000,
                iccProfile = byteArrayOf(0x41, 0x42, 0x43, 0x44),
                rowsTopDown = listOf(listOf(argb(0x80, 0x12, 0x34, 0x56))),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(argb(0x80, 0x12, 0x34, 0x56), bitmap!!.getPixel(0, 0))
        assertNull(codec.getICCProfile())
    }

    @Test
    fun `rejects truncated 16-bit bitfield masks`() {
        val bytes = ByteArray(14 + 40)
        writeFileAndInfoHeader(bytes, width = 1, height = 1, bitsPerPixel = 16, compression = 3, pixelOffset = 14 + 40 + 12, topDown = false)

        assertNull(BmpCodec.Decoder.make(bytes))
    }

    @Test
    fun `decodes RLE8 palette BMP pixels`() {
        val palette = intArrayOf(BLACK, RED, GREEN, BLUE, WHITE)
        val codec = BmpCodec.Decoder.make(
            rleBmp(
                width = 4,
                height = 2,
                bitsPerPixel = 8,
                compression = 1,
                palette = palette,
                encoded = byteArrayOf(
                    4, 1, // bottom row: four red pixels
                    0, 0, // end of line
                    0, 4, 2, 3, 4, 0, // top row absolute: green, blue, white, black
                    0, 0,
                    0, 1,
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(GREEN, bitmap!!.getPixel(0, 0))
        assertEquals(BLUE, bitmap.getPixel(1, 0))
        assertEquals(WHITE, bitmap.getPixel(2, 0))
        assertEquals(BLACK, bitmap.getPixel(3, 0))
        assertEquals(RED, bitmap.getPixel(0, 1))
        assertEquals(RED, bitmap.getPixel(3, 1))
    }

    @Test
    fun `decodes RLE4 palette BMP pixels with delta`() {
        val palette = intArrayOf(BLACK, RED, GREEN, BLUE, WHITE)
        val codec = BmpCodec.Decoder.make(
            rleBmp(
                width = 4,
                height = 2,
                bitsPerPixel = 4,
                compression = 2,
                palette = palette,
                encoded = byteArrayOf(
                    2, 0x12, // bottom row: red, green
                    0, 2, 1, 0, // delta one pixel right
                    1, 0x30, // bottom row x=3: blue
                    0, 0,
                    0, 4, 0x24, 0x10, // top row absolute: green, white, red, black
                    0, 0,
                    0, 1,
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(GREEN, bitmap!!.getPixel(0, 0))
        assertEquals(WHITE, bitmap.getPixel(1, 0))
        assertEquals(RED, bitmap.getPixel(2, 0))
        assertEquals(BLACK, bitmap.getPixel(3, 0))
        assertEquals(RED, bitmap.getPixel(0, 1))
        assertEquals(GREEN, bitmap.getPixel(1, 1))
        assertEquals(BLACK, bitmap.getPixel(2, 1))
        assertEquals(BLUE, bitmap.getPixel(3, 1))
    }

    @Test
    fun `reports incomplete RLE input while keeping header accepted`() {
        val codec = BmpCodec.Decoder.make(
            rleBmp(
                width = 2,
                height = 1,
                bitsPerPixel = 8,
                compression = 1,
                palette = intArrayOf(BLACK, RED),
                encoded = byteArrayOf(2),
            ),
        )!!

        val (_, result) = codec.getImage()
        assertEquals(Codec.Result.kIncompleteInput, result)
    }

    @Test
    fun `reports 8888 sRGB unpremul info`() {
        val codec = BmpCodec.Decoder.make(
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
        val codec = BmpCodec.Decoder.make(
            indexedBmp(
                width = rows[0].size,
                height = rows.size,
                bitsPerPixel = bitsPerPixel,
                palette = palette,
                indexRowsTopDown = rows,
            ),
        )!!
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
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

    private fun rleBmp(
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        compression: Int,
        palette: IntArray,
        encoded: ByteArray,
    ): ByteArray {
        val paletteBytes = palette.size * 4
        val pixelOffset = 14 + 40 + paletteBytes
        val out = ByteArray(pixelOffset + encoded.size)
        writeFileAndInfoHeader(out, width, height, bitsPerPixel, compression, pixelOffset, topDown = false)
        writeI32LE(out, 34, encoded.size)
        writeI32LE(out, 46, palette.size)
        for (i in palette.indices) {
            val c = palette[i]
            val off = 14 + 40 + i * 4
            out[off] = b(c).toByte()
            out[off + 1] = g(c).toByte()
            out[off + 2] = r(c).toByte()
        }
        System.arraycopy(encoded, 0, out, pixelOffset, encoded.size)
        return out
    }

    private fun bitfieldsBmp(
        width: Int,
        height: Int,
        redMask: Int,
        greenMask: Int,
        blueMask: Int,
        topDown: Boolean = false,
        rowsTopDown: List<List<Int>>,
    ): ByteArray {
        val bitsPerPixel = 16
        val rowBytes = rowBytes(width, bitsPerPixel)
        val pixelOffset = 14 + 40 + 12
        val out = ByteArray(pixelOffset + rowBytes * height)
        writeFileAndInfoHeader(out, width, height, bitsPerPixel, 3, pixelOffset, topDown)
        writeI32LE(out, 14 + 40, redMask)
        writeI32LE(out, 14 + 44, greenMask)
        writeI32LE(out, 14 + 48, blueMask)
        for (dy in 0 until height) {
            val fileRow = if (topDown) dy else height - 1 - dy
            val row = pixelOffset + fileRow * rowBytes
            for (x in 0 until width) {
                writeU16LE(out, row + x * 2, packBitfields(rowsTopDown[dy][x], redMask, greenMask, blueMask))
            }
        }
        return out
    }

    private fun v4BitfieldsBmp(
        width: Int,
        height: Int,
        headerSize: Int = 108,
        redMask: Int,
        greenMask: Int,
        blueMask: Int,
        alphaMask: Int,
        iccProfile: ByteArray = ByteArray(0),
        rowsTopDown: List<List<Int>>,
    ): ByteArray {
        val bitsPerPixel = 32
        val rowBytes = rowBytes(width, bitsPerPixel)
        val pixelOffset = 14 + headerSize
        val out = ByteArray(pixelOffset + rowBytes * height + iccProfile.size)
        writeV4OrV5Header(out, width, height, bitsPerPixel, headerSize, pixelOffset, iccProfile)
        writeI32LE(out, 14 + 40, redMask)
        writeI32LE(out, 14 + 44, greenMask)
        writeI32LE(out, 14 + 48, blueMask)
        writeI32LE(out, 14 + 52, alphaMask)
        for (dy in 0 until height) {
            val fileRow = height - 1 - dy
            val row = pixelOffset + fileRow * rowBytes
            for (x in 0 until width) {
                writeI32LE(out, row + x * 4, packBitfields(rowsTopDown[dy][x], redMask, greenMask, blueMask, alphaMask))
            }
        }
        iccProfile.copyInto(out, pixelOffset + rowBytes * height)
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

    private fun writeV4OrV5Header(
        out: ByteArray,
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        headerSize: Int,
        pixelOffset: Int,
        iccProfile: ByteArray,
    ) {
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeI32LE(out, 2, out.size)
        writeI32LE(out, 10, pixelOffset)
        writeI32LE(out, 14, headerSize)
        writeI32LE(out, 18, width)
        writeI32LE(out, 22, height)
        writeU16LE(out, 26, 1)
        writeU16LE(out, 28, bitsPerPixel)
        writeI32LE(out, 30, 3)
        if (headerSize >= 124 && iccProfile.isNotEmpty()) {
            writeI32LE(out, 14 + 112, pixelOffset + rowBytes(width, bitsPerPixel) * height)
            writeI32LE(out, 14 + 116, iccProfile.size)
        }
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

    private fun packBitfields(color: Int, redMask: Int, greenMask: Int, blueMask: Int): Int =
        packMasked(r(color), redMask) or packMasked(g(color), greenMask) or packMasked(b(color), blueMask)

    private fun packBitfields(color: Int, redMask: Int, greenMask: Int, blueMask: Int, alphaMask: Int): Int =
        packBitfields(color, redMask, greenMask, blueMask) or packMasked(a(color), alphaMask)

    private fun packMasked(component: Int, mask: Int): Int {
        val shift = Integer.numberOfTrailingZeros(mask)
        val max = mask ushr shift
        return ((component * max + 127) / 255) shl shift
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
