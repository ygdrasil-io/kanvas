package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThinRectsGpuCoverageTest {
    @Test
    fun `matches Skia coverage for a translated quarter pixel green rect`() {
        GpuAvailability.requireWebGpu()

        val gm = ThinRectsGm()
        val rendered = SkiaGmRenderer.render(gm)
        val reference = ReferenceManager.loadReference("/reference/thinrects.png")
        val pixelOffset = (42 * gm.width + 61) * 4

        val expected = reference[pixelOffset + 1].toInt() and 0xFF
        val actual = rendered.rgba[pixelOffset + 1].toInt() and 0xFF

        assertTrue(
            kotlin.math.abs(expected - actual) <= 1,
            "expected $expected but was $actual",
        )
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
