package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.graphiks.kanvas.codec.test.CodecTestFixtures.decodePixels
import java.util.ServiceLoader
import java.util.Base64

class CodecAllKotlinAssemblyTest {
    @Test
    fun `registers pure kotlin decoders in dispatch order`() {
        val decoders = Codec.Decoders.all()

        assertEquals(
            listOf(
                "png",
                "jpeg-ls",
                "jpeg",
                "gif",
                "bmp",
                "webp",
                "wbmp",
                "avif",
                "jpegxl",
                "ico",
                "raw",
            ),
            decoders.map { it.name },
        )
    }

    @Test
    fun `assembles pure kotlin decoder providers only`() {
        val providers = ServiceLoader.load(CodecDecoderProvider::class.java)
            .map { it::class.qualifiedName ?: it.javaClass.name }
            .sorted()
            .toList()

        assertEquals(
            listOf(
                "org.graphiks.kanvas.codec.ExtendedCodecDecoderProvider",
                "org.graphiks.kanvas.codec.IcoKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.bmp.BmpKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.gif.GifKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.jpeg.JpegKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.jpegls.JpegLsKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.png.PngKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.wbmp.WbmpKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.webp.WebpKotlinDecoderProvider",
            ),
            providers,
        )
    }

    @Test
    fun `decodes shared rgba png fixture through kotlin assembly`() {
        val codec = Codec.MakeFromData(CodecTestFixtures.simpleRgbaPng())

        assertNotNull(codec)
        val actual = decodePixels(codec!!)
        assertEquals(CodecTestFixtures.SIMPLE_RGBA_PIXELS.size, actual.size)
        for (y in CodecTestFixtures.SIMPLE_RGBA_PIXELS.indices) {
            assertArrayEquals(CodecTestFixtures.SIMPLE_RGBA_PIXELS[y], actual[y], "row=$y")
        }
    }

    @Test
    fun `routes SOF55 to JPEG-LS before the classic JPEG provider`() {
        val fixture = Base64.getDecoder().decode("/9j/9wALCAAEAAgBAREA/9oACAEBAAAAAAAAAYCV8/9g/9k=")

        val codec = Codec.MakeFromData(fixture)

        assertNotNull(codec)
        assertEquals("org.graphiks.kanvas.codec.jpegls.JpegLsCodec", codec!!::class.qualifiedName)
    }

    @Test
    fun `decodes shared rgba png fixture through Kanvas bitmap facade`() {
        val codec = Codec.MakeFromData(CodecTestFixtures.simpleRgbaPng())

        assertNotNull(codec)
        val info = codec!!.getKanvasInfo()
        val (bitmap, result) = codec.getKanvasImage()

        assertEquals(ColorType.RGBA_8888, info.colorType)
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(Color.fromArgbInt(CodecTestFixtures.RED), bitmap!!.getPixel(0, 0))
        assertEquals(Color.fromArgbInt(CodecTestFixtures.GREEN), bitmap.getPixel(1, 0))
        assertEquals(Color.fromArgbInt(CodecTestFixtures.BLUE), bitmap.getPixel(0, 1))
        assertEquals(Color.fromArgbInt(CodecTestFixtures.WHITE), bitmap.getPixel(1, 1))
    }
}
