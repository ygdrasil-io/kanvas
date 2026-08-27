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

class NearestHalfPixelImageGmTest {
    @Test
    fun `nearest shader uses inherited geometry transforms without a local matrix refusal`() {
        GpuAvailability.requireWebGpu()

        val gm = NearestHalfPixelImageGm()
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
        assertEquals(2, result.dispatchedCount, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(62_040, comparison.totalPixels)
        assertEquals(45_568, comparison.matchingPixels)
        assertEquals(73.44938749194068, comparison.similarity)
        assertNotNull(comparison.diffRgba)
        assertEquals(
            "max=255, 255, 255, 0 mean=153.28897523069452, 174.97620203982515, 106.67799902865468, 0.0 ssim=0.4371369865048657 error=0.11321576212690106",
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
