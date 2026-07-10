package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.gm.blur.ImageBlurRepeatUnclippedGm
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageFilterBlurContractTest {
    @Test
    fun `backdrop save layer reports the stable refusal`() {
        GpuAvailability.requireWebGpu()
        val surface = Surface(width = 64, height = 64)
        val crop = Rect.fromLTRB(0f, 0f, 64f, 64f)

        surface.canvas().saveLayer(
            SaveLayerRec(
                backdrop = ImageFilter.Crop(
                    crop,
                    TileMode.DECAL,
                    ImageFilter.Blur(1f, 1f, input = ImageFilter.Crop(crop, TileMode.MIRROR)),
                ),
            ),
        )
        surface.canvas().restore()

        val result = surface.render()

        assertTrue(result.diagnostics.entries.any { it.reason == "unsupported.layer.backdrop_filter" })
        assertTrue(result.stats.opsRefused > 0)
    }

    @Test
    fun `repeat image blur GM reports the stable refusal`() {
        GpuAvailability.requireWebGpu()

        val result = SkiaGmRenderer.render(ImageBlurRepeatUnclippedGm())

        assertTrue(result.diagnostics.any { it.endsWith("unsupported.image-filter.blur.tile-mode") })
        assertTrue(result.refusedCount > 0)
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
