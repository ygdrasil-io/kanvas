package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AAXfermodesRegressionTest {
    @Test
    fun `clipped AA blend mode grid retains visible pixels`() {
        GpuAvailability.requireWebGpu()

        val gm = AAXfermodesGm()
        val actual = SkiaGmRenderer.render(gm).rgba
        val reference = ReferenceManager.loadReference("/reference/${gm.name}.png")
        val matchingPixels = actual.asList()
            .zip(reference.asList())
            .chunked(4)
            .count { channels -> channels.all { (actualByte, referenceByte) -> actualByte == referenceByte } }

        assertTrue(
            matchingPixels > 10_000,
            "expected clipped blend grid to retain visible reference pixels, matched=$matchingPixels",
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
