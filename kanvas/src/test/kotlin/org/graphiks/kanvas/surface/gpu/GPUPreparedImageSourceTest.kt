package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
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
}
