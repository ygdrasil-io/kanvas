package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConicalGradients2ptOutsideGM

/**
 * G-suivi (round 15) cross-test : `ConicalGradients2ptOutsideGM` --
 * dither-on twin of the existing `ConicalGradients2ptOutsideNoDitherGM`
 * cross-test (G4.4.5, 95.11 %). Same 840 x 815 grid of 4 columns x 5
 * rows of 100 x 100 conical-gradient rects under `kClamp`. Only the
 * upstream reference differs (`paint.isDither = true` produces a
 * dithered PNG).
 *
 * Our rasterizer never applies dither so GPU output is identical to the
 * nodither sibling. The dithered upstream reference happens to land
 * byte-exact for the gradient stops used here (mostly pure
 * red / green / blue / white / black at the cell corners), so we match
 * 100 % despite the dither flag.
 */
class ConicalGradients2ptOutsideDitherWebGpuTest {

    @Test
    fun `ConicalGradients2ptOutsideGM dither renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptOutsideGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_outside")
                ?: error("original-888/gradients_2pt_conical_outside.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptOutsideDitherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_outside-gpu")
            // Landing score 100.00 %. Floor set 0.05 % below for scoring
            // drift headroom.
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptOutsideGM (dither) regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
