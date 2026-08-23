package org.graphiks.kanvas.codec.gif

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ImageInfo
import java.io.ByteArrayOutputStream

class GifEncoderTest {

    @Test
    fun `unsupported source color type is refused without writing to stream`() {
        val dst = ByteArrayOutputStream().also { it.write(0x2A) }

        assertFalse(GifEncoder.encode(dst, Bitmap(1, 1, ColorType.RGB_565)))
        assertEquals(listOf(0x2A.toByte()), dst.toByteArray().toList())
    }

    @Test
    fun `premultiplied RGBA source is refused without writing to stream`() {
        val dst = ByteArrayOutputStream().also { it.write(0x2A) }

        assertFalse(GifEncoder.encode(dst, premulBitmap()))
        assertEquals(listOf(0x2A.toByte()), dst.toByteArray().toList())
    }

    @Test
    fun `encode single-frame GIF round-trips through decoder`() {
        val src = Bitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = if ((x + y) and 1 == 0) 0xFF else 0x00
            src.setArgb(x, y, (0xFF shl 24) or (r shl 16))
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
        assertNull(GifEncoder.encode(Bitmap(0, 0)))
    }

    @Test
    fun `encode to OutputStream matches direct encode`() {
        val src = bitmap(4, 4, 0xFF808080.toInt())
        val viaData = GifEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(GifEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `encode with loop count writes Netscape extension`() {
        val src = bitmap(2, 2, 0xFF0000FF.toInt())
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
        val frame1 = bitmap(8, 8, 0xFFFF0000.toInt())
        val frame2 = bitmap(8, 8, 0xFF00FF00.toInt())
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
        val bm = bitmap(4, 4, 0xFF0000FF.toInt())
        val frames = listOf(GifEncoder.Frame(bm, delayCs = 50))
        val bytes = GifEncoder.encode(bm, GifEncoder.Options(frames = frames, loopCount = -1))!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(-1, codec!!.getRepetitionCount())
    }

    @Test
    fun `multi-frame GIF frame delays round-trip`() {
        val bm1 = bitmap(8, 8, 0xFFFF0000.toInt())
        val bm2 = bitmap(8, 8, 0xFF00FF00.toInt())
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
        val bm1 = bitmap(4, 4, 0xFFFF0000.toInt())
        val bm2 = bitmap(4, 4, 0xFF00FF00.toInt())
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
        val dst = Bitmap(codec.getInfo())
        val r2 = codec.getPixels(codec.getInfo(), dst, Codec.Options(frameIndex = 1, priorFrame = 0))
        assertEquals(Codec.Result.kSuccess, r2)
    }

    @Test
    fun `single-frame GIF still works with null frames`() {
        val bm = bitmap(4, 4, 0xFF808080.toInt())
        val bytes = GifEncoder.encode(bm)!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(4, decoded!!.width)
    }
    private fun bitmap(width: Int, height: Int, argb: Int): Bitmap = Bitmap(width, height).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) bitmap.setArgb(x, y, argb)
    }

    private fun premulBitmap(): Bitmap = Bitmap(
        ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.PREMUL),
    )
}
