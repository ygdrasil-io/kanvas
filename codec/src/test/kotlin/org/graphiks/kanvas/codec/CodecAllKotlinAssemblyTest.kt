package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.graphiks.kanvas.codec.test.CodecTestFixtures.decodePixels
import java.util.ServiceLoader

class CodecAllKotlinAssemblyTest {
    @Test
    fun `registers pure kotlin decoders in dispatch order`() {
        val decoders = SkCodec.Decoders.all()

        assertEquals(
            listOf(
                "png",
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
                "org.graphiks.kanvas.codec.png.PngKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.wbmp.WbmpKotlinDecoderProvider",
                "org.graphiks.kanvas.codec.webp.WebpKotlinDecoderProvider",
            ),
            providers,
        )
    }

    @Test
    fun `decodes shared rgba png fixture through kotlin assembly`() {
        val codec = SkCodec.MakeFromData(CodecTestFixtures.simpleRgbaPng())

        assertNotNull(codec)
        val actual = decodePixels(codec!!)
        assertEquals(CodecTestFixtures.SIMPLE_RGBA_PIXELS.size, actual.size)
        for (y in CodecTestFixtures.SIMPLE_RGBA_PIXELS.indices) {
            assertArrayEquals(CodecTestFixtures.SIMPLE_RGBA_PIXELS[y], actual[y], "row=$y")
        }
    }
}
