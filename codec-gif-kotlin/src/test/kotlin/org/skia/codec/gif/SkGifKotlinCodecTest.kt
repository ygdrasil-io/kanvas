package org.skia.codec.gif

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import java.util.ServiceLoader

class SkGifKotlinCodecTest {

    @Test
    fun `registers decoder through service loader`() {
        val names = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { provider -> provider.decoders() }
            .map { decoder -> decoder.name }

        assertTrue("gif" in names)
    }

    @Test
    fun `rejects invalid signatures`() {
        assertFalse(SkGifKotlinCodec.Decoder.matches(ByteArray(0)))
        assertFalse(SkGifKotlinCodec.Decoder.matches("not-a-gif".toByteArray()))
        assertNull(SkGifKotlinCodec.Decoder.make("GIF00a".toByteArray()))
        assertNull(SkGifKotlinCodec.Decoder.make("GIF89a".toByteArray()))
    }

    @Test
    fun `decodes minimal single frame gif`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 1,
                height = 1,
                palette = intArrayOf(RED, BLUE),
                indexes = intArrayOf(0),
            ),
        )

        assertNotNull(codec)
        assertTrue(codec is SkGifKotlinCodec)
        assertEquals(SkEncodedImageFormat.kGIF, codec!!.getEncodedFormat())
        assertEquals(1, codec.getFrameCount())
        assertEquals(1, codec.dimensions().width)
        assertEquals(1, codec.dimensions().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `applies graphic control transparent index`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 2,
                height = 1,
                palette = intArrayOf(RED, BLUE),
                indexes = intArrayOf(0, 1),
                transparentIndex = 1,
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(TRANSPARENT, bitmap.getPixel(1, 0))
    }

    @Test
    fun `uses local color table when present`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 2,
                height = 1,
                palette = intArrayOf(RED, BLUE),
                indexes = intArrayOf(0, 1),
                localPalette = intArrayOf(GREEN, YELLOW),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(GREEN, bitmap!!.getPixel(0, 0))
        assertEquals(YELLOW, bitmap.getPixel(1, 0))
    }

    private fun gif(
        width: Int,
        height: Int,
        palette: IntArray,
        indexes: IntArray,
        transparentIndex: Int = -1,
        localPalette: IntArray? = null,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out.addAscii("GIF89a")
        out.addU16LE(width)
        out.addU16LE(height)
        out += (0x80 or 0x70 or colorTableSizeBits(palette.size)).toByte()
        out += 0.toByte()
        out += 0.toByte()
        out.addColorTable(palette)

        if (transparentIndex >= 0) {
            out += 0x21.toByte()
            out += 0xF9.toByte()
            out += 4.toByte()
            out += 1.toByte()
            out.addU16LE(0)
            out += transparentIndex.toByte()
            out += 0.toByte()
        }

        out += 0x2C.toByte()
        out.addU16LE(0)
        out.addU16LE(0)
        out.addU16LE(width)
        out.addU16LE(height)
        if (localPalette != null) {
            out += (0x80 or colorTableSizeBits(localPalette.size)).toByte()
            out.addColorTable(localPalette)
        } else {
            out += 0.toByte()
        }
        out += MIN_CODE_SIZE.toByte()
        out.addSubBlocks(lzwData(indexes))
        out += 0x3B.toByte()
        return out.toByteArray()
    }

    private fun lzwData(indexes: IntArray): ByteArray {
        val clearCode = 1 shl MIN_CODE_SIZE
        val endCode = clearCode + 1
        val codes = intArrayOf(clearCode, *indexes, endCode)
        val out = ArrayList<Byte>()
        var current = 0
        var bits = 0
        for (code in codes) {
            current = current or (code shl bits)
            bits += MIN_CODE_SIZE + 1
            while (bits >= 8) {
                out += (current and 0xFF).toByte()
                current = current ushr 8
                bits -= 8
            }
        }
        if (bits > 0) out += (current and 0xFF).toByte()
        return out.toByteArray()
    }

    private fun colorTableSizeBits(size: Int): Int {
        assertTrue(size >= 2)
        assertTrue(size.countOneBits() == 1)
        return size.countTrailingZeroBits() - 1
    }

    private fun ArrayList<Byte>.addAscii(value: String) {
        value.forEach { this += it.code.toByte() }
    }

    private fun ArrayList<Byte>.addU16LE(value: Int) {
        this += value.toByte()
        this += (value ushr 8).toByte()
    }

    private fun ArrayList<Byte>.addColorTable(colors: IntArray) {
        for (color in colors) {
            this += r(color).toByte()
            this += g(color).toByte()
            this += b(color).toByte()
        }
    }

    private fun ArrayList<Byte>.addSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(255, data.size - offset)
            this += size.toByte()
            for (i in 0 until size) this += data[offset + i]
            offset += size
        }
        this += 0.toByte()
    }

    private fun r(c: Int): Int = (c ushr 16) and 0xFF
    private fun g(c: Int): Int = (c ushr 8) and 0xFF
    private fun b(c: Int): Int = c and 0xFF
}

private const val MIN_CODE_SIZE: Int = 2
private const val TRANSPARENT: Int = 0
private const val RED: Int = -0x10000
private const val GREEN: Int = -0xff0100
private const val BLUE: Int = -0xffff01
private const val YELLOW: Int = -0x100
