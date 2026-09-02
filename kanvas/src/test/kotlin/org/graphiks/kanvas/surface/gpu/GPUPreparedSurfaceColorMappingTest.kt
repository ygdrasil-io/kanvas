package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat as CanonicalGPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

class GPUPreparedSurfaceColorMappingTest {
    @Test
    fun `public default maps to the exact canonical prepared color pair without changing its label`() {
        val config = RenderConfig.DEFAULT

        assertEquals(GPUColorFormat.RGBA8_UNORM_SRGB, config.gpuColorFormat)
        assertEquals("rgba8unorm-srgb", config.gpuColorFormat.gpuLabel)
        val ready = assertIs<GPUPreparedSurfaceColorMapping.Ready>(config.mapPreparedGpuColorConfig())
        assertEquals(CanonicalGPUColorFormat.RGBA8UnormSrgb, ready.physicalFormat)
        assertEquals(GPUColorInterpretation.LinearPremul, ready.interpretation)
    }

    @Test
    fun `public linear rgba format is refused with a stable code`() {
        val refused = assertIs<GPUPreparedSurfaceColorMapping.Refused>(
            RenderConfig(gpuColorFormat = GPUColorFormat.RGBA8_UNORM).mapPreparedGpuColorConfig(),
        )

        assertEquals("unsupported.surface.gpu-color-format.rgba8-unorm", refused.code)
    }

    @Test
    fun `public bgra format maps to the exact canonical prepared color pair`() {
        val ready = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM).mapPreparedGpuColorConfig(),
        )

        assertEquals(CanonicalGPUColorFormat.BGRA8Unorm, ready.physicalFormat)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, ready.interpretation)
    }
}
