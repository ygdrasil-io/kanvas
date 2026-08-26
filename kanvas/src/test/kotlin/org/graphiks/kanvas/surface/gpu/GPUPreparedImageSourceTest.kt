package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.math.color.ColorARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GPUPreparedImageSourceTest {
    @Test
    fun `prepared surface source maps SRGB premultiplied pixels to decoded CPU input`() {
        val result = GPUPreparedSurfaceImageSource.prepare(
            Image(1, 1, ColorType.RGBA_8888, "caller", byteArrayOf(1, 2, 3, 4), alphaType = AlphaType.PREMUL),
        )

        assertIs<GPUPreparedImageArtifactResult.Ready>(result)
    }

    @Test
    fun `prepared surface source accepts unpremultiplied caller pixels`() {
        val result = GPUPreparedSurfaceImageSource.prepare(
            Image(1, 1, ColorType.RGBA_8888, "caller", byteArrayOf(1, 2, 3, 4), alphaType = AlphaType.UNPREMUL),
        )

        assertIs<GPUPreparedImageArtifactResult.Ready>(result)
    }

    @Test
    fun `A8 image defaults are premultiplied for prepared surface uploads`() {
        val constructorImage = Image(
            1,
            1,
            ColorType.ALPHA_8,
            "constructor-a8",
            byteArrayOf(0x80.toByte()),
        )
        val factoryImage = Image.fromPixels(
            1,
            1,
            byteArrayOf(0x80.toByte()),
            ColorType.ALPHA_8,
            "factory-a8",
        )

        for (image in listOf(constructorImage, factoryImage)) {
            assertEquals(AlphaType.PREMUL, image.alphaType)
            assertIs<GPUPreparedImageArtifactResult.Ready>(
                GPUPreparedSurfaceImageSource.prepare(image),
            )
        }
    }

    @Test
    fun `prepared surface source converts the bitmap formats used by all bitmap configs`() {
        val images = listOf(
            Bitmap(1, 1, ColorType.RGB_565).also {
                it.setPixel(0, 0, ColorARGB.fromRGBA(1f, 0f, 0f, 1f))
            },
            Bitmap(1, 1, ColorType.ARGB_4444).also {
                it.setPixel(0, 0, ColorARGB.fromRGBA(1f, 0.5f, 0.25f, 0.5f))
            },
            Bitmap(1, 1, ColorType.RGBA_F16).also {
                it.setPixel(0, 0, ColorARGB.fromRGBA(0.5f, 0.25f, 0.75f, 0.5f))
            },
            Bitmap(1, 1, ColorType.GRAY_8).also {
                it.setPixel(0, 0, ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f))
            },
        ).map { requireNotNull(it.toImageOrNull()) }

        images.forEach { image ->
            assertIs<GPUPreparedImageArtifactResult.Ready>(
                GPUPreparedSurfaceImageSource.prepare(image),
                image.colorType.name,
            )
        }
    }

    @Test
    fun `prepared surface source refuses a pixel format outside the bounded contract`() {
        val result = GPUPreparedSurfaceImageSource.prepare(
            Image(1, 1, ColorType.RGB_888X, "unsupported", byteArrayOf(1, 2, 3, 4)),
        )

        val refused = assertIs<GPUPreparedImageArtifactResult.Refused>(result)
        assertEquals("unsupported.image.pixel.format", refused.code)
    }
}
