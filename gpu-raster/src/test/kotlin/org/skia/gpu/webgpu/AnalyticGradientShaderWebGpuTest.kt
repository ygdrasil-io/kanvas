package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.AnalyticGradientShaderGM

/**
 * Cross-test : `AnalyticGradientShaderGM` on the GPU backend.
 *
 * 8 x 4 grid of `kClamp` linear gradients with 1 to 8 interpolation
 * intervals each. Mixed smooth transitions + hardstops (duplicate
 * positions) across rows ; per-cell colour count ranges from 2 to 16
 * (which is exactly our `MAX_GRADIENT_STOPS` cap, so this also stress-
 * tests the upper-bound of the uniform stop table). Translates between
 * cells are axis-aligned, so each cell hits the G4.1
 * `path.isRect + axis-aligned-CTM` fast path.
 */
class AnalyticGradientShaderWebGpuTest {

    @Test
    fun `AnalyticGradientShaderGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = AnalyticGradientShaderGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("analytic_gradients")
                ?: error("original-888/analytic_gradients.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[AnalyticGradientShaderWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "analytic_gradients-gpu")
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "AnalyticGradientShaderGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
