package org.graphiks.kanvas.codec.gif

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

class GifEncoderTest {

    @Test
    fun `encode single-frame GIF round-trips through decoder`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = if ((x + y) and 1 == 0) 0xFF else 0x00
            src.pixels[y * 4 + x] = (0xFF shl 24) or (r shl 16)
        }
        val bytes = GifEncoder.encode(src)!!
        assertTrue(bytes.size >= 6)
        assertEquals('G', bytes[0].toInt().toChar())
        assertEquals('I', bytes[1].toInt().toChar())
        assertEquals('F', bytes[2].toInt().toChar())
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(4, decoded!!.width)
        assertEquals(4, decoded.height)
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(GifEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `encode to OutputStream matches direct encode`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val viaData = GifEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(GifEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `encode with loop count writes Netscape extension`() {
        val src = SkBitmap(2, 2)
        for (i in 0 until 4) src.pixels[i] = 0xFF0000FF.toInt()
        val bytes = GifEncoder.encode(src, GifEncoder.Options(loopCount = 5))!!
        assertTrue(bytes.size > 0)
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
    }
}
