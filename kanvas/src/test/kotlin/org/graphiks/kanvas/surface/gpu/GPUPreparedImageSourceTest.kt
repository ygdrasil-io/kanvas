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
    fun `prepared surface source refuses unpremultiplied caller pixels`() {
        val result = GPUPreparedSurfaceImageSource.prepare(
            Image(1, 1, ColorType.RGBA_8888, "caller", byteArrayOf(1, 2, 3, 4), alphaType = AlphaType.UNPREMUL),
        )

        assertEquals("image.alpha.unpremul", assertIs<GPUPreparedImageArtifactResult.Refused>(result).code)
    }
}
