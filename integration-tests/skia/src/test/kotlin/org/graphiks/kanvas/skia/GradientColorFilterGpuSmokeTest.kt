package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.gm.composite.ColorFiltersGm
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GradientColorFilterGpuSmokeTest {
    @Test
    fun `translated multi stop gradient color filter renders visible pixels`() {
        GpuAvailability.requireWebGpu()

        val result = SkiaGmRenderer.render(ColorFiltersGm())

        assertTrue(result.rgba.asList().chunked(4).any { pixel ->
            pixel.any { channel -> (channel.toInt() and 0xFF) != 255 }
        })
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
