package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitmapPremulCompositeConvergenceTest {
    @Test
    fun `reused images render through composite lanes with the reference pixels`() {
        GpuAvailability.requireWebGpu()

        val gm = BitmapPremulGm()
        val result = SkiaGmRenderer.render(gm)
        val reference = ReferenceManager.loadReference("/reference/${gm.referenceName}.png")

        assertEquals(0, result.refusedCount, result.diagnostics.joinToString())
        assertEquals(5, result.dispatchedCount, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertArrayEquals(reference, result.rgba)
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
