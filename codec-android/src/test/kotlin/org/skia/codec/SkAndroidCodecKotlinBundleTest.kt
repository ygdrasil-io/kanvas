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

    private fun fiveByFivePng(): ByteArray =
        CodecTestFixtures.rgbaPng(
            List(5) { y ->
                IntArray(5) { x -> argbAt(x, y) }
            },
        )

    private fun argbAt(x: Int, y: Int): Int =
        (0xFF shl 24) or ((0x20 + x * 0x21) shl 16) or ((0x30 + y * 0x17) shl 8) or (0x40 + x + y)

    private fun assertRgba(buffer: ByteBuffer, rowBytes: Int, x: Int, y: Int, color: Int) {
        val offset = y * rowBytes + x * 4
        assertEquals((color ushr 16) and 0xFF, buffer.get(offset).toInt() and 0xFF, "r[$x,$y]")
        assertEquals((color ushr 8) and 0xFF, buffer.get(offset + 1).toInt() and 0xFF, "g[$x,$y]")
        assertEquals(color and 0xFF, buffer.get(offset + 2).toInt() and 0xFF, "b[$x,$y]")
        assertEquals((color ushr 24) and 0xFF, buffer.get(offset + 3).toInt() and 0xFF, "a[$x,$y]")
    }
}
