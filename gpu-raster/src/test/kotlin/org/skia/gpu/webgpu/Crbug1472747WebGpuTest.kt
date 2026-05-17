package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.Crbug1472747GM

/**
 * Cross-test : `Crbug1472747GM` (`crbug_1472747`) on the GPU backend.
 *
 * 400 x 400 single drawPath : inner + outer ovals at r=31000 / r=31005
 * filled with `kEvenOdd` to produce a thin ring. Each oval is built
 * from two 180-degree `arcTo` half-arcs as Canvas2D would. Tests the
 * even-odd fill rule on multi-contour input with conic-flattened arcs
 * at extreme radius (G3.3b.3b on top of conic flattening from G3.3b.1).
 */
class Crbug1472747WebGpuTest {

    @Test
    fun `Crbug1472747GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = Crbug1472747GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("crbug_1472747")
                ?: error("original-888/crbug_1472747.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[Crbug1472747WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "crbug_1472747-gpu")
            val floor = 98.10
            assertTrue(
                cmp.similarity >= floor,
                "Crbug1472747GM regressed below floor : ${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/crbug_1472747-gpu.png.",
            )
        }
    }
}
