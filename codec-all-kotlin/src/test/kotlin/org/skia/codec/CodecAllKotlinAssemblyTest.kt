package org.skia.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
                "org.skia.codec.ExtendedCodecDecoderProvider",
                "org.skia.codec.IcoKotlinDecoderProvider",
                "org.skia.codec.bmp.BmpKotlinDecoderProvider",
                "org.skia.codec.gif.GifKotlinDecoderProvider",
                "org.skia.codec.jpeg.JpegKotlinDecoderProvider",
                "org.skia.codec.png.PngKotlinDecoderProvider",
                "org.skia.codec.wbmp.WbmpKotlinDecoderProvider",
                "org.skia.codec.webp.WebpKotlinDecoderProvider",
            ),
            providers,
        )
    }
}
