package org.skia.codec

import org.graphiks.math.SkIRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.codec.test.CodecTestFixtures
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import java.nio.ByteBuffer

class SkAndroidCodecKotlinBundleTest {
    @Test
    fun `MakeFromData decodes PNG through codec-all-kotlin providers`() {
        val codec = SkAndroidCodec.MakeFromData(CodecTestFixtures.simpleRgbaPng())

        assertNotNull(codec)
        assertEquals(SkEncodedImageFormat.kPNG, codec!!.getEncodedFormat())
        assertEquals(2, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)
    }

    @Test
    fun `getAndroidPixels crops downsamples and writes RGBA ByteBuffer`() {
        val codec = SkAndroidCodec.MakeFromData(fiveByFivePng())!!
        val subset = SkIRect.MakeLTRB(1, 1, 5, 5)
        val info = SkImageInfo.MakeN32(width = 2, height = 2)
        val rowBytes = info.minRowBytes()
        val pixels = ByteBuffer.allocate(rowBytes * info.height)

        val result = codec.getAndroidPixels(
            info = info,
            pixels = pixels,
            rowBytes = rowBytes,
            options = SkAndroidCodec.AndroidOptions(sampleSize = 2, subset = subset),
        )

        assertEquals(SkCodec.Result.kSuccess, result)
        assertRgba(pixels, rowBytes, x = 0, y = 0, color = argbAt(1, 1))
        assertRgba(pixels, rowBytes, x = 1, y = 0, color = argbAt(3, 1))
        assertRgba(pixels, rowBytes, x = 0, y = 1, color = argbAt(1, 3))
        assertRgba(pixels, rowBytes, x = 1, y = 1, color = argbAt(3, 3))
    }

    @Test
    fun `getAndroidPixels crops downsamples JPEG through codec-all-kotlin providers`() {
        val codec = SkAndroidCodec.MakeFromData(CodecTestFixtures.simpleGrayscaleJpeg(width = 16, height = 16))!!
        val pixels = decodeSampledRgba(codec, subset = SkIRect.MakeLTRB(4, 4, 16, 16), sampleSize = 4)

        assertEquals(SkEncodedImageFormat.kJPEG, codec.getEncodedFormat())
        assertRgba(pixels.buffer, pixels.rowBytes, x = 0, y = 0, color = 0xFF808080.toInt())
        assertRgba(pixels.buffer, pixels.rowBytes, x = 2, y = 2, color = 0xFF808080.toInt())
    }

    @Test
    fun `getAndroidPixels crops downsamples GIF through codec-all-kotlin providers`() {
        val indexes = List(4) { y -> IntArray(4) { x -> (x + y) and 3 } }
        val palette = intArrayOf(0xFF101820.toInt(), 0xFF305060.toInt(), 0xFF708090.toInt(), 0xFFA0B0C0.toInt())
        val codec = SkAndroidCodec.MakeFromData(CodecTestFixtures.indexedGif(indexes, palette))!!
        val pixels = decodeSampledRgba(codec, subset = SkIRect.MakeLTRB(1, 1, 4, 4), sampleSize = 2)

        assertEquals(SkEncodedImageFormat.kGIF, codec.getEncodedFormat())
        assertRgba(pixels.buffer, pixels.rowBytes, x = 0, y = 0, color = palette[indexes[1][1]])
    }

    @Test
    fun `getAndroidPixels crops downsamples BMP through codec-all-kotlin providers`() {
        val rows = patternedRows(width = 4, height = 4)
        val codec = SkAndroidCodec.MakeFromData(CodecTestFixtures.rgbBmp(rows))!!
        val pixels = decodeSampledRgba(codec, subset = SkIRect.MakeLTRB(1, 1, 4, 4), sampleSize = 2)

        assertEquals(SkEncodedImageFormat.kBMP, codec.getEncodedFormat())
        assertRgba(pixels.buffer, pixels.rowBytes, x = 0, y = 0, color = rows[1][1])
    }

    private fun fiveByFivePng(): ByteArray =
        CodecTestFixtures.rgbaPng(
            List(5) { y ->
                IntArray(5) { x -> argbAt(x, y) }
            },
        )

    private fun decodeSampledRgba(codec: SkAndroidCodec, subset: SkIRect, sampleSize: Int): PixelBuffer {
        val info = SkImageInfo.MakeN32(width = subset.width() / sampleSize, height = subset.height() / sampleSize)
        val rowBytes = info.minRowBytes()
        val buffer = ByteBuffer.allocate(rowBytes * info.height)

        val result = codec.getAndroidPixels(
            info = info,
            pixels = buffer,
            rowBytes = rowBytes,
            options = SkAndroidCodec.AndroidOptions(sampleSize = sampleSize, subset = subset),
        )

        assertEquals(SkCodec.Result.kSuccess, result)
        return PixelBuffer(buffer, rowBytes)
    }

    private fun patternedRows(width: Int, height: Int): List<IntArray> =
        List(height) { y ->
            IntArray(width) { x ->
                (0xFF shl 24) or ((0x24 + x * 0x25) shl 16) or ((0x18 + y * 0x31) shl 8) or (0x44 + x * 3 + y)
            }
        }

    private fun argbAt(x: Int, y: Int): Int =
        (0xFF shl 24) or ((0x20 + x * 0x21) shl 16) or ((0x30 + y * 0x17) shl 8) or (0x40 + x + y)

    private fun assertRgba(buffer: ByteBuffer, rowBytes: Int, x: Int, y: Int, color: Int) {
        val offset = y * rowBytes + x * 4
        assertEquals((color ushr 16) and 0xFF, buffer.get(offset).toInt() and 0xFF, "r[$x,$y]")
        assertEquals((color ushr 8) and 0xFF, buffer.get(offset + 1).toInt() and 0xFF, "g[$x,$y]")
        assertEquals(color and 0xFF, buffer.get(offset + 2).toInt() and 0xFF, "b[$x,$y]")
        assertEquals((color ushr 24) and 0xFF, buffer.get(offset + 3).toInt() and 0xFF, "a[$x,$y]")
    }

    private data class PixelBuffer(val buffer: ByteBuffer, val rowBytes: Int)
}
