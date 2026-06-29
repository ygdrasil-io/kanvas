package org.graphiks.kanvas.codec.gif

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.SkCodec
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
    fun `deinterlaces image data in gif pass order`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 1,
                height = 8,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                frames = listOf(
                    GifFrameSpec(
                        left = 0,
                        top = 0,
                        width = 1,
                        height = 8,
                        indexes = intArrayOf(0, 1, 2, 3, 0, 1, 2, 3),
                        interlaced = true,
                    ),
                ),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(RED, bitmap.getPixel(0, 1))
        assertEquals(BLUE, bitmap.getPixel(0, 2))
        assertEquals(GREEN, bitmap.getPixel(0, 3))
        assertEquals(GREEN, bitmap.getPixel(0, 4))
        assertEquals(BLUE, bitmap.getPixel(0, 5))
        assertEquals(YELLOW, bitmap.getPixel(0, 6))
        assertEquals(YELLOW, bitmap.getPixel(0, 7))
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

    @Test
    fun `disposal none and do not dispose keep composed canvas`() {
        for (disposal in listOf(DISPOSAL_NONE, DISPOSAL_DO_NOT_DISPOSE)) {
            val codec = SkGifKotlinCodec.Decoder.make(
                gif(
                    width = 3,
                    height = 1,
                    palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                    frames = listOf(
                        GifFrameSpec(0, 0, 3, 1, intArrayOf(0, 0, 0)),
                        GifFrameSpec(1, 0, 1, 1, intArrayOf(2), disposal = disposal),
                        GifFrameSpec(2, 0, 1, 1, intArrayOf(1)),
                    ),
                ),
            )!!

            val frameInfo = codec.getFrameInfo()
            assertEquals(1, frameInfo[2].requiredFrame)

            val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
            val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 2))
            assertEquals(SkCodec.Result.kSuccess, result)
            assertEquals(RED, dst.getPixel(0, 0))
            assertEquals(BLUE, dst.getPixel(1, 0))
            assertEquals(GREEN, dst.getPixel(2, 0))
        }
    }

    @Test
    fun `exposes netscape loop count and skips comment extensions before frames`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 2,
                height = 1,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                extensions = listOf(
                    netscapeLoopExtension(loopCount = 3),
                    commentExtension(":codec:gif"),
                ),
                frames = listOf(
                    GifFrameSpec(0, 0, 2, 1, intArrayOf(0, 1), delayCs = 4),
                    GifFrameSpec(1, 0, 1, 1, intArrayOf(2), delayCs = 9),
                ),
            ),
        )

        assertNotNull(codec)
        assertEquals(2, codec!!.getFrameCount())
        assertEquals(3, codec.getRepetitionCount())
        assertEquals(40, codec.getFrameInfo()[0].durationMs)
        assertEquals(90, codec.getFrameInfo()[1].durationMs)

        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = 1))
        assertEquals(SkCodec.Result.kSuccess, result)
        assertEquals(RED, dst.getPixel(0, 0))
        assertEquals(BLUE, dst.getPixel(1, 0))
    }

    @Test
    fun `maps netscape zero loop count to infinite repetition`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 1,
                height = 1,
                palette = intArrayOf(RED, GREEN),
                extensions = listOf(netscapeLoopExtension(loopCount = 0)),
                frames = listOf(GifFrameSpec(0, 0, 1, 1, intArrayOf(0), delayCs = 4)),
            ),
        )

        assertNotNull(codec)
        assertEquals(SkCodec.kRepetitionCountInfinite, codec!!.getRepetitionCount())
    }

    @Test
    fun `decodes representative multi frame fixture corpus`() {
        val codec = SkGifKotlinCodec.Decoder.make(
            gif(
                width = 4,
                height = 4,
                palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
                backgroundIndex = 3,
                extensions = listOf(
                    netscapeLoopExtension(loopCount = 2),
                    commentExtension("synthetic multi-frame corpus"),
                ),
                frames = listOf(
                    GifFrameSpec(
                        left = 0,
                        top = 0,
                        width = 4,
                        height = 4,
                        indexes = intArrayOf(
                            0, 0, 0, 0,
                            0, 0, 0, 0,
                            0, 0, 0, 0,
                            0, 0, 0, 0,
                        ),
                        delayCs = 5,
                    ),
                    GifFrameSpec(
                        left = 1,
                        top = 1,
                        width = 2,
                        height = 2,
                        indexes = intArrayOf(
                            0, 1,
                            1, 0,
                        ),
                        delayCs = 6,
                        localPalette = intArrayOf(GREEN, BLUE),
                    ),
                    GifFrameSpec(
                        left = 0,
                        top = 0,
                        width = 1,
                        height = 4,
                        indexes = intArrayOf(2, 3, 2, 3),
                        delayCs = 7,
                        interlaced = true,
                    ),
                    GifFrameSpec(
                        left = 2,
                        top = 0,
                        width = 2,
                        height = 2,
                        indexes = intArrayOf(
                            1, 2,
                            2, 1,
                        ),
                        delayCs = 8,
                        disposal = DISPOSAL_BACKGROUND,
                    ),
                    GifFrameSpec(
                        left = 1,
                        top = 2,
                        width = 2,
                        height = 2,
                        indexes = intArrayOf(
                            1, 3,
                            3, 1,
                        ),
                        delayCs = 9,
                        disposal = DISPOSAL_PREVIOUS,
                    ),
                    GifFrameSpec(
                        left = 3,
                        top = 3,
                        width = 1,
                        height = 1,
                        indexes = intArrayOf(2),
                        delayCs = 10,
                    ),
                ),
            ),
        )!!

        val frameInfo = codec.getFrameInfo()
        assertEquals(6, codec.getFrameCount())
        assertEquals(listOf(50, 60, 70, 80, 90, 100), frameInfo.map { it.durationMs })
        assertEquals(listOf(SkCodec.kNoFrame, 0, 1, 2, 3, 3), frameInfo.map { it.requiredFrame })

        assertFramePixels(
            codec,
            frameIndex = 0,
            expected = listOf(
                intArrayOf(RED, RED, RED, RED),
                intArrayOf(RED, RED, RED, RED),
                intArrayOf(RED, RED, RED, RED),
                intArrayOf(RED, RED, RED, RED),
            ),
        )
        assertFramePixels(
            codec,
            frameIndex = 1,
            expected = listOf(
                intArrayOf(RED, RED, RED, RED),
                intArrayOf(RED, GREEN, BLUE, RED),
                intArrayOf(RED, BLUE, GREEN, RED),
                intArrayOf(RED, RED, RED, RED),
            ),
        )
        assertFramePixels(
            codec,
            frameIndex = 2,
            expected = listOf(
                intArrayOf(BLUE, RED, RED, RED),
                intArrayOf(BLUE, GREEN, BLUE, RED),
                intArrayOf(YELLOW, BLUE, GREEN, RED),
                intArrayOf(YELLOW, RED, RED, RED),
            ),
        )
        assertFramePixels(
            codec,
            frameIndex = 3,
            expected = listOf(
                intArrayOf(BLUE, RED, GREEN, BLUE),
                intArrayOf(BLUE, GREEN, BLUE, GREEN),
                intArrayOf(YELLOW, BLUE, GREEN, RED),
                intArrayOf(YELLOW, RED, RED, RED),
            ),
        )
        assertFramePixels(
            codec,
            frameIndex = 4,
            expected = listOf(
                intArrayOf(BLUE, RED, YELLOW, YELLOW),
                intArrayOf(BLUE, GREEN, YELLOW, YELLOW),
                intArrayOf(YELLOW, GREEN, YELLOW, RED),
                intArrayOf(YELLOW, YELLOW, GREEN, RED),
            ),
        )
        assertFramePixels(
            codec,
            frameIndex = 5,
            expected = listOf(
                intArrayOf(BLUE, RED, YELLOW, YELLOW),
                intArrayOf(BLUE, GREEN, YELLOW, YELLOW),
                intArrayOf(YELLOW, BLUE, GREEN, RED),
                intArrayOf(YELLOW, RED, RED, BLUE),
            ),
        )
    }

    @Test
    fun `rejects truncated image data sub-block`() {
        val data = gif(
            width = 1,
            height = 1,
            palette = intArrayOf(RED, BLUE),
            indexes = intArrayOf(0),
        )
        val corrupted = data.copyOf()
        corrupted[firstImageDataSubBlockSizeOffset(corrupted)] = 127.toByte()

        assertNull(SkGifKotlinCodec.Decoder.make(corrupted))
    }

    @Test
    fun `rejects truncated extension sub-block`() {
        val dataWithTrailer = gif(
            width = 1,
            height = 1,
            palette = intArrayOf(RED, BLUE),
            extensions = listOf(
                byteArrayOf(0x21, 0xFE.toByte(), 0x05, 'o'.code.toByte(), 'o'.code.toByte()),
            ),
            frames = emptyList(),
        )
        val truncatedAtEof = dataWithTrailer.copyOf(dataWithTrailer.size - 1)

        assertNull(
            SkGifKotlinCodec.Decoder.make(
                truncatedAtEof,
            ),
        )
    }

    @Test
    fun `rejects frame rect outside canvas`() {
        assertNull(
            SkGifKotlinCodec.Decoder.make(
                gif(
                    width = 2,
                    height = 1,
                    palette = intArrayOf(RED, BLUE),
                    frames = listOf(
                        GifFrameSpec(1, 0, 2, 1, intArrayOf(0, 1)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `rejects decoded index outside palette`() {
        assertNull(
            SkGifKotlinCodec.Decoder.make(
                gif(
                    width = 1,
                    height = 1,
                    palette = intArrayOf(RED, BLUE),
                    indexes = intArrayOf(3),
                ),
            ),
        )
    }

    @Test
    fun `rejects invalid lzw stream`() {
        assertNull(
            SkGifKotlinCodec.Decoder.make(
                gif(
                    width = 1,
                    height = 1,
                    palette = intArrayOf(RED, BLUE),
                    frames = listOf(
                        GifFrameSpec(
                            left = 0,
                            top = 0,
                            width = 1,
                            height = 1,
                            indexes = intArrayOf(0),
                            rawImageData = byteArrayOf(0x04),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `make eagerly validates later frames before first image request`() {
        val data = gif(
            width = 2,
            height = 1,
            palette = intArrayOf(RED, GREEN, BLUE, YELLOW),
            frames = listOf(
                GifFrameSpec(0, 0, 2, 1, intArrayOf(0, 1), delayCs = 4),
                GifFrameSpec(
                    left = 1,
                    top = 0,
                    width = 1,
                    height = 1,
                    indexes = intArrayOf(2),
                    delayCs = 9,
                    rawImageData = byteArrayOf(0x04),
                ),
            ),
        )

        assertNull(SkGifKotlinCodec.Decoder.make(data))
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
        extensions: List<ByteArray> = emptyList(),
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
        for (extension in extensions) {
            for (byte in extension) out += byte
        }

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
            val interlaceBit = if (frame.interlaced) 0x40 else 0
            if (frame.localPalette != null) {
                out += (0x80 or interlaceBit or colorTableSizeBits(frame.localPalette.size)).toByte()
                out.addColorTable(frame.localPalette)
            } else {
                out += interlaceBit.toByte()
            }
            out += MIN_CODE_SIZE.toByte()
            out.addSubBlocks(frame.rawImageData ?: lzwData(frame.indexes))
        }
        out += 0x3B.toByte()
        return out.toByteArray()
    }

    private fun netscapeLoopExtension(loopCount: Int): ByteArray {
        val out = ArrayList<Byte>()
        out += 0x21.toByte()
        out += 0xFF.toByte()
        out += 11.toByte()
        out.addAscii("NETSCAPE2.0")
        out += 3.toByte()
        out += 1.toByte()
        out.addU16LE(loopCount)
        out += 0.toByte()
        return out.toByteArray()
    }

    private fun commentExtension(value: String): ByteArray {
        val out = ArrayList<Byte>()
        out += 0x21.toByte()
        out += 0xFE.toByte()
        out.addSubBlocks(value.encodeToByteArray())
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

    private fun firstImageDataSubBlockSizeOffset(data: ByteArray): Int {
        val imageSeparatorOffset = data.indexOf(0x2C.toByte())
        assertTrue(imageSeparatorOffset >= 0)
        return imageSeparatorOffset + 10 + 1
    }

    private fun assertFramePixels(codec: SkCodec, frameIndex: Int, expected: List<IntArray>) {
        val dst = SkBitmap(codec.getInfo().width, codec.getInfo().height)
        val result = codec.getPixels(codec.getInfo(), dst, SkCodec.Options(frameIndex = frameIndex))
        assertEquals(SkCodec.Result.kSuccess, result)
        for (y in expected.indices) {
            for (x in expected[y].indices) {
                assertEquals(expected[y][x], dst.getPixel(x, y), "frame=$frameIndex x=$x y=$y")
            }
        }
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
        val interlaced: Boolean = false,
        val rawImageData: ByteArray? = null,
    )
}

private const val MIN_CODE_SIZE: Int = 2
private const val DISPOSAL_NONE: Int = 0
private const val DISPOSAL_DO_NOT_DISPOSE: Int = 1
private const val DISPOSAL_BACKGROUND: Int = 2
private const val DISPOSAL_PREVIOUS: Int = 3
private const val TRANSPARENT: Int = 0
private const val RED: Int = -0x10000
private const val GREEN: Int = -0xff0100
private const val BLUE: Int = -0xffff01
private const val YELLOW: Int = -0x100
