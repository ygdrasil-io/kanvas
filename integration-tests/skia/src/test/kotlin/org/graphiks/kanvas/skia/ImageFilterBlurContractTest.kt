package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.gm.blur.ImageBlurRepeatUnclippedGm
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageFilterBlurContractTest {
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
