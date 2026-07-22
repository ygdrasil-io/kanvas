package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(CanonicalGPUColorFormat.RGBA8Unorm, ready.physicalFormat)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, ready.interpretation)
    }

    @Test
    fun `public linear rgba format is refused with a stable code`() {
        val refused = assertIs<GPUPreparedSurfaceColorMapping.Refused>(
            RenderConfig(gpuColorFormat = GPUColorFormat.RGBA8_UNORM).mapPreparedGpuColorConfig(),
        )

        assertEquals("unsupported.surface.gpu-color-format.rgba8-unorm", refused.code)
    }

    @Test
    fun `public bgra format is refused with a stable code`() {
        val refused = assertIs<GPUPreparedSurfaceColorMapping.Refused>(
            RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM).mapPreparedGpuColorConfig(),
        )

        assertEquals("unsupported.surface.gpu-color-format.bgra8-unorm", refused.code)
    }

    @Test
    fun `surface keeps GPU route internals behind renderViaGpu after product cutover`() {
        val surfaceSource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt",
        ).readText()

        assertContains(
            surfaceSource,
            "fun render(): RenderResult = renderViaGpu(buffer, width, height, format, config)",
        )
        assertFalse(surfaceSource.contains("mapPreparedGpuColorConfig"))
        assertFalse(surfaceSource.contains("prepareSceneFrameSession"))
        assertFalse(surfaceSource.contains("GPUFrameCoordinator"))
    }
}
