package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.GradientDirtyLaundryGM

/**
 * G-suivi (round 13) cross-test : `GradientDirtyLaundryGM` -- 640 x 615
 * canvas (BG 0xFFDDDDDD) with three 100 x 100 drawRects stacked
 * vertically at (20, 20), each filled with a 40-stop kClamp gradient
 * (linear / radial / sweep). The 40 entries replay the 5-colour
 * `{R, G, B, W, K}` pattern eight times -- a regression marker for
 * banding in the gradient sampler's per-stop lerp arithmetic.
 *
 * In scope on the GPU side : linear (G4.0), radial (G4.1), sweep
 * (G4.3) all on rect with kClamp tile mode and axis-aligned CTM.
 */
class GradientDirtyLaundryWebGpuTest {

    @Test
    fun `GradientDirtyLaundryGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = GradientDirtyLaundryGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradient_dirty_laundry")
                ?: error("original-888/gradient_dirty_laundry.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[GradientDirtyLaundryWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradient_dirty_laundry-gpu")
            // Landing score 94.48%. Drift concentrated on the dense
            // 40-stop sampler's per-stop lerp boundaries -- a few LSB
            // of channel jitter per row leaves a couple of thousand
            // pixels outside the textual tolerance band.
            val floor = 94.40
            assertTrue(
                cmp.similarity >= floor,
                "GradientDirtyLaundryGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
