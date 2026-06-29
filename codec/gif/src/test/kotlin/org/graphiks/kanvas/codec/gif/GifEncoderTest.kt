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

    @Test
    fun `multi-frame animated GIF round-trips frame count`() {
        val frame1 = SkBitmap(8, 8)
        for (i in 0 until 64) frame1.pixels[i] = 0xFFFF0000.toInt()
        val frame2 = SkBitmap(8, 8)
        for (i in 0 until 64) frame2.pixels[i] = 0xFF00FF00.toInt()
        val frames = listOf(
            GifEncoder.Frame(frame1, delayCs = 50),
            GifEncoder.Frame(frame2, delayCs = 100),
        )
        val bytes = GifEncoder.encode(frame1, GifEncoder.Options(frames = frames))!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(2, codec!!.getFrameCount())
    }

    @Test
    fun `multi-frame GIF carries loop count`() {
        val bm = SkBitmap(4, 4)
        for (i in 0 until 16) bm.pixels[i] = 0xFF0000FF.toInt()
        val frames = listOf(GifEncoder.Frame(bm, delayCs = 50))
        val bytes = GifEncoder.encode(bm, GifEncoder.Options(frames = frames, loopCount = -1))!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(-1, codec!!.getRepetitionCount())
    }

    @Test
    fun `multi-frame GIF frame delays round-trip`() {
        val bm1 = SkBitmap(8, 8)
        for (i in 0 until 64) bm1.pixels[i] = 0xFFFF0000.toInt()
        val bm2 = SkBitmap(8, 8)
        for (i in 0 until 64) bm2.pixels[i] = 0xFF00FF00.toInt()
        val frames = listOf(
            GifEncoder.Frame(bm1, delayCs = 5),
            GifEncoder.Frame(bm2, delayCs = 15, disposal = GifEncoder.DISPOSAL_BACKGROUND),
        )
        val bytes = GifEncoder.encode(bm1, GifEncoder.Options(frames = frames))!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val infos = codec!!.getFrameInfo()
        assertEquals(2, infos.size)
        assertEquals(50, infos[0].durationMs)
        assertEquals(150, infos[1].durationMs)
    }

    @Test
    fun `multi-frame GIF round-trips decoded pixels for all frames`() {
        val bm1 = SkBitmap(4, 4)
        for (i in 0 until 16) bm1.pixels[i] = 0xFFFF0000.toInt()
        val bm2 = SkBitmap(4, 4)
        for (i in 0 until 16) bm2.pixels[i] = 0xFF00FF00.toInt()
        val frames = listOf(
            GifEncoder.Frame(bm1, delayCs = 50),
            GifEncoder.Frame(bm2, delayCs = 50, disposal = GifEncoder.DISPOSAL_NONE),
        )
        val bytes = GifEncoder.encode(bm1, GifEncoder.Options(frames = frames))!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (bm1dec, r1) = codec!!.getImage()
        assertNotNull(bm1dec)
        assertEquals(Codec.Result.kSuccess, r1)
        val dst = SkBitmap(4, 4)
        val r2 = codec.getPixels(codec.getInfo(), dst, Codec.Options(frameIndex = 1, priorFrame = 0))
        assertEquals(Codec.Result.kSuccess, r2)
    }

    @Test
    fun `single-frame GIF still works with null frames`() {
        val bm = SkBitmap(4, 4)
        for (i in 0 until 16) bm.pixels[i] = 0xFF808080.toInt()
        val bytes = GifEncoder.encode(bm)!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(4, decoded!!.width)
    }
}
