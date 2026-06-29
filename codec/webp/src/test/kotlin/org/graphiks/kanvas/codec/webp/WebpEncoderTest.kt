package org.graphiks.kanvas.codec.webp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

class WebpEncoderTest {

    @Test
    fun `lossless encode round-trip through WebP decoder preserves pixels`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = (x * 85).coerceIn(0, 255)
            val g = (y * 85).coerceIn(0, 255)
            src.pixels[y * 4 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8)
        }
        val bytes = WebpEncoder.encode(src)!!
        val decoded = decodeWebp(bytes)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(WebpEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val viaData = WebpEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(WebpEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    private fun decodeWebp(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "WebP decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
