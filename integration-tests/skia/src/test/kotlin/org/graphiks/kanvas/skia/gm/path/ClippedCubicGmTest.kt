package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClippedCubicGmTest {
    @Test
    fun `clipped cubic GM renders through the bounded path route`() {
        GpuAvailability.requireWebGpu()

        val gm = ClippedCubicGm()
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
        assertTrue(result.dispatchedCount > 0)
        assertTrue(comparison.similarity >= gm.minSimilarity)
        println(
            "task116.gm-evidence gm=${gm.name} route=bounded-cubic-path " +
                "dispatch=${result.dispatchedCount} refuse=${result.refusedCount} " +
                "similarity=${comparison.similarity} " +
                "meanError=${comparison.meanChannelError}",
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
