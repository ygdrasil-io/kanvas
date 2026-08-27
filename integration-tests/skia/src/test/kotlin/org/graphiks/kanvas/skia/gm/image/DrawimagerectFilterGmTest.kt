package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawimagerectFilterGmTest {
    @Test
    fun `half pixel image shader local sampling renders without a fallback refusal`() {
        GpuAvailability.requireWebGpu()

        val gm = DrawimagerectFilterGm()
        val result = SkiaGmRenderer.render(gm)
        val reference = ReferenceManager.loadReference("/reference/${gm.referenceName}.png")
        val comparison = ComparisonUtils.compareRgba(
            actual = result.rgba,
            reference = reference,
            width = result.width,
            height = result.height,
            tolerance = gm.tolerance,
            minSimilarity = gm.minSimilarity,
        )

        assertEquals(0, result.refusedCount, result.diagnostics.joinToString())
        assertEquals(4, result.dispatchedCount, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(10_800, comparison.totalPixels)
        assertEquals(3_104, comparison.matchingPixels)
        assertEquals(28.74074074074074, comparison.similarity)
        assertNotNull(comparison.diffRgba)
        assertEquals(
            "max=255, 255, 255, 0 mean=84.53079521829522, 84.53079521829522, 84.53079521829522, 0.0 ssim=0.5766025363789481 error=0.1771647603485839",
            "max=${comparison.maxDiff.joinToString()} mean=${comparison.meanDiff.joinToString()} ssim=${comparison.ssim} error=${comparison.meanChannelError}",
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
