package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.AnalyticAntialiasInverseGM

/**
 * Cross-test : `AnalyticAntialiasInverseGM` on the GPU backend.
 *
 * 800 x 800 single drawn path : a 30-radius circle at (100, 100) flipped
 * to `kInverseWinding` and painted red, AA on. The visible field is the
 * complement of the disc — red everywhere except inside the circle.
 * Exercises the single-contour convex inverse-fill route landed in
 * G3.3b.3b (stencil compare `Equal` 0, full-viewport cover quad).
 */
class AnalyticAntialiasInverseWebGpuTest {

    @Test
    fun `AnalyticAntialiasInverseGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = AnalyticAntialiasInverseGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("analytic_antialias_inverse")
                ?: error("original-888/analytic_antialias_inverse.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[AnalyticAntialiasInverseWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "analytic_antialias_inverse-gpu")
            val floor = 99.93
            assertTrue(
                cmp.similarity >= floor,
                "AnalyticAntialiasInverseGM regressed below floor : ${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/analytic_antialias_inverse-gpu.png.",
            )
        }
    }
}
