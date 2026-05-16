package org.skia.codec.gif

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * D3.3 verification suite for [SkGifCodec].
 *
 * Covers GIF-87a / GIF-89a signature dispatch, decode plumbing,
 * geometry agreement with `getInfo`, and the sRGB / `kUnpremul`
 * tagging that mirrors the JPEG / WBMP / BMP D3.3 family.
 */
class SkGifCodecTest {

    @Test
    fun `MakeFromData rejects non-GIF bytes`() {
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData("nope".toByteArray()))
        // GIF-style but broken : prefix correct, suffix wrong.
        val bad = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x35, 0x61, 0x00)
        assertNull(SkCodec.MakeFromData(bad))
    }

    @Test
    fun `MakeFromData dispatches a synthetic GIF to SkGifCodec`() {
        val bytes = synthGif(width = 6, height = 4)
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec)
        assertTrue(codec is SkGifCodec)
        assertEquals(SkEncodedImageFormat.kGIF, codec!!.getEncodedFormat())
        assertEquals(6, codec.dimensions().width)
        assertEquals(4, codec.dimensions().height)
    }

    @Test
    fun `getImage produces an 8888 sRGB unpremul bitmap`() {
        val codec = SkCodec.MakeFromData(synthGif(8, 8))!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB())
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(8, bitmap!!.width)
        assertEquals(8, bitmap.height)
    }

    @Test
    fun `MakeFromStream is equivalent to MakeFromData`() {
        val bytes = synthGif(4, 4)
        val viaData = SkCodec.MakeFromData(bytes)!!
        val viaStream = SkCodec.MakeFromStream(ByteArrayInputStream(bytes))!!
        assertEquals(viaData.dimensions(), viaStream.dimensions())
        assertEquals(viaData.getEncodedFormat(), viaStream.getEncodedFormat())
    }

    private fun synthGif(width: Int, height: Int): ByteArray {
        // GIF writer wants a paletted (TYPE_BYTE_INDEXED) or RGB image ;
        // TYPE_INT_RGB works fine and exercises the typical path.
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            img.setRGB(x, y, (r shl 16) or 0x40)
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "gif", it) }.toByteArray()
    }
}
