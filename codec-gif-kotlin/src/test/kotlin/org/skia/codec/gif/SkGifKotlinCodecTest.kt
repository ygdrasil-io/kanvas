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
import org.skia.foundation.SkBitmap
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

    @Test
    fun `exposes partial frame rect delay and decodes selected frame`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 3,
                height = 2,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                frames = listOf(
                    GifFrameSpec(
                        left = 0,
                        top = 0,
                        width = 3,
                        height = 2,
                        indexes = intArrayOf(0, 0, 0, 0, 0, 0),
                    ),
                    GifFrameSpec(
                        left = 1,
                        top = 0,
                        width = 1,
                        height = 2,
                        indexes = intArrayOf(2, 3),
                        delayCs = 7,
                    ),
                ),
            ),
        )!!

        val frameInfo = codec.getFrameInfo()
        assertEquals(2, frameInfo.size)
        assertEquals(70, frameInfo[1].durationMs)
        assertEquals(0, frameInfo[1].requiredFrame)
        assertEquals(1, frameInfo[1].frameRect.left)
        assertEquals(0, frameInfo[1].frameRect.top)
        assertEquals(1, frameInfo[1].frameRect.width())
        assertEquals(2, frameInfo[1].frameRect.height())

        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 1))
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(RED, dst.getPixel(0, 0))
        assertEquals(BLUE, dst.getPixel(1, 0))
        assertEquals(RED, dst.getPixel(2, 0))
        assertEquals(RED, dst.getPixel(0, 1))
        assertEquals(YELLOW, dst.getPixel(1, 1))
        assertEquals(RED, dst.getPixel(2, 1))
    }

    @Test
    fun `restore to background uses logical background color`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 3,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                backgroundIndex = 1,
                frames = listOf(
                    GifFrameSpec(0, 0, 3, 1, intArrayOf(0, 0, 0)),
                    GifFrameSpec(1, 0, 1, 1, intArrayOf(2), disposal = DISPOSAL_BACKGROUND),
                    GifFrameSpec(2, 0, 1, 1, intArrayOf(2)),
                ),
            ),
        )!!

        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 2))
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(RED, dst.getPixel(0, 0))
        assertEquals(GREEN, dst.getPixel(1, 0))
        assertEquals(BLUE, dst.getPixel(2, 0))
    }

    @Test
    fun `restore to background uses transparency when background index is transparent`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 3,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                backgroundIndex = 1,
                frames = listOf(
                    GifFrameSpec(0, 0, 3, 1, intArrayOf(0, 0, 0)),
                    GifFrameSpec(
                        left = 1,
                        top = 0,
                        width = 1,
                        height = 1,
                        indexes = intArrayOf(2),
                        disposal = DISPOSAL_BACKGROUND,
                        transparentIndex = 1,
                    ),
                    GifFrameSpec(2, 0, 1, 1, intArrayOf(2)),
                ),
            ),
        )!!

        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 2))
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(RED, dst.getPixel(0, 0))
        assertEquals(TRANSPARENT, dst.getPixel(1, 0))
        assertEquals(BLUE, dst.getPixel(2, 0))
    }

    @Test
    fun `restore to previous restores canvas before the disposed frame`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 3,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                frames = listOf(
                    GifFrameSpec(0, 0, 3, 1, intArrayOf(0, 0, 0)),
                    GifFrameSpec(1, 0, 1, 1, intArrayOf(2), disposal = DISPOSAL_PREVIOUS),
                    GifFrameSpec(2, 0, 1, 1, intArrayOf(1)),
                ),
            ),
        )!!

        val frameInfo = codec.getFrameInfo()
        assertEquals(SkCodec.kNoFrame, frameInfo[0].requiredFrame)
        assertEquals(0, frameInfo[1].requiredFrame)
        assertEquals(0, frameInfo[2].requiredFrame)

        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 2))
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(RED, dst.getPixel(0, 0))
        assertEquals(RED, dst.getPixel(1, 0))
        assertEquals(GREEN, dst.getPixel(2, 0))
    }

    private fun gif(
        width: Int,
        height: Int,
        palette: IntArray,
        indexes: IntArray,
        transparentIndex: Int = -1,
        localPalette: IntArray? = null,
    ): ByteArray {
        return gif(
            width = width,
            height = height,
            palette = palette,
            frames = listOf(
                GifFrameSpec(
                    left = 0,
                    top = 0,
                    width = width,
                    height = height,
                    indexes = indexes,
                    transparentIndex = transparentIndex,
                    localPalette = localPalette,
                ),
            ),
        )
    }

    private fun gif(
        width: Int,
        height: Int,
        palette: IntArray,
        backgroundIndex: Int = 0,
        frames: List<GifFrameSpec>,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out.addAscii("GIF89a")
        out.addU16LE(width)
        out.addU16LE(height)
        out += (0x80 or 0x70 or colorTableSizeBits(palette.size)).toByte()
        out += backgroundIndex.toByte()
        out += 0.toByte()
        out.addColorTable(palette)

        for (frame in frames) {
            out += 0x21.toByte()
            out += 0xF9.toByte()
            out += 4.toByte()
            out += ((frame.disposal shl 2) or if (frame.transparentIndex >= 0) 1 else 0).toByte()
            out.addU16LE(frame.delayCs)
            out += maxOf(frame.transparentIndex, 0).toByte()
            out += 0.toByte()

            out += 0x2C.toByte()
            out.addU16LE(frame.left)
            out.addU16LE(frame.top)
            out.addU16LE(frame.width)
            out.addU16LE(frame.height)
            if (frame.localPalette != null) {
                out += (0x80 or colorTableSizeBits(frame.localPalette.size)).toByte()
                out.addColorTable(frame.localPalette)
            } else {
                out += 0.toByte()
            }
            out += MIN_CODE_SIZE.toByte()
            out.addSubBlocks(lzwData(frame.indexes))
        }
        out += 0x3B.toByte()
        return out.toByteArray()
    }

    private fun lzwData(indexes: IntArray): ByteArray {
        val clearCode = 1 shl MIN_CODE_SIZE
        val endCode = clearCode + 1
        val codes = buildList {
            var offset = 0
            while (offset < indexes.size) {
                add(clearCode)
                add(indexes[offset++])
                if (offset < indexes.size) add(indexes[offset++])
            }
            add(endCode)
        }
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

    private data class GifFrameSpec(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val indexes: IntArray,
        val delayCs: Int = 0,
        val disposal: Int = DISPOSAL_NONE,
        val transparentIndex: Int = -1,
        val localPalette: IntArray? = null,
    )
}

private const val MIN_CODE_SIZE: Int = 2
private const val DISPOSAL_NONE: Int = 0
private const val DISPOSAL_BACKGROUND: Int = 2
private const val DISPOSAL_PREVIOUS: Int = 3
private const val TRANSPARENT: Int = 0
private const val RED: Int = -0x10000
private const val GREEN: Int = -0xff0100
private const val BLUE: Int = -0xffff01
private const val YELLOW: Int = -0x100
