package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawimagerectFilterGmTest {
    @Test
    fun `half pixel image shader local sampling renders without a fallback refusal`() {
        GpuAvailability.requireWebGpu()

        val result = SkiaGmRenderer.render(DrawimagerectFilterGm())

        assertEquals(0, result.refusedCount, result.diagnostics.joinToString())
        assertEquals(4, result.dispatchedCount, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertTrue(result.rgba.any { (it.toInt() and 0xff) != 255 })
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
