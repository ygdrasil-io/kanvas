package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientLinearNoditherGM

/**
 * Cross-test : `ShallowGradientLinearNoditherGM` on the GPU backend.
 *
 * Same shape as the dithered variant (already ratcheted at 100 % in
 * G4.1) : single 800x800 drawRect filled with a `SkLinearGradient`
 * (kClamp tile mode) from `(0, 0)` to `(800, 800)`, colours
 * `0xFF555555 -> 0xFF444444`. The "nodither" suffix only changes the
 * reference PNG's encoder side -- the GM body is identical to the
 * dithered one in our port. Validates that our gradient pipeline
 * matches the no-dither reference too (we never dither anyway).
 */
class ShallowGradientLinearNoditherWebGpuTest {

    @Test
    fun `ShallowGradientLinearNoditherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientLinearNoditherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_linear_nodither")
                ?: error("original-888/shallow_gradient_linear_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientLinearNoditherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_linear_nodither-gpu")
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientLinearNoditherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
