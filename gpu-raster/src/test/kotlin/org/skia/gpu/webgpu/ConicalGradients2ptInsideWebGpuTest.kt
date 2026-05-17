package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConicalGradients2ptInsideGM
import org.skia.tests.ConicalGradients2ptInsideMirrorGM
import org.skia.tests.ConicalGradients2ptInsideRepeatGM

/**
 * G4.4.1 cross-test : `ConicalGradients2ptInsideGM` -- 840 x 815
 * grid of 4 columns x 7 rows of conical gradient rects. All 28 cases
 * place both circle centres "inside" the cell, so the GPU paths most
 * likely to exercise here are :
 *  - kRadial sub-case for the Center / CenterReversed / ZeroRadCenter
 *    rows (3 / 7 rows ; c0 == c1).
 *  - kFocal well-behaved (focal-inside) sub-case for the rest of the
 *    rows where the focal point lies inside the end circle. The
 *    "Flip" / "ZeroRadFlip" rows route through the same kFocal pipeline
 *    via the `fIsSwapped` post-pass + the `negate_x` post-pass.
 *
 * Anything still missing on the GPU side (focal-outside, focal-on-
 * circle, kStrip) falls through to the solid-color machinery on those
 * cells -- that depresses the similarity floor here. The score floor is
 * deliberately set well below 100% : this test only locks the
 * non-regression of the kRadial slice + the new focal-inside slice ;
 * once the other variants land the floor can be raised.
 */
class ConicalGradients2ptInsideWebGpuTest {

    @Test
    fun `ConicalGradients2ptInsideGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptInsideGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_inside_nodither")
                ?: error("original-888/gradients_2pt_conical_inside_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptInsideWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_inside_nodither-gpu")
            // Floor is set permissively -- the GM mixes kRadial + focal-inside
            // (both supported) with a small number of cells whose makers
            // produce focal-on-circle / focal-outside (still on the solid-
            // color fallback). Raise as the remaining sub-cases land.
            val floor = 80.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptInsideGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    /**
     * G4.4.2 cross-test : `ConicalGradients2ptInsideRepeatGM` -- same
     * grid as the kClamp variant above, with kRepeat tile mode. Exercises
     * the new `fs_repeat` entry point of both the kRadial and the
     * focal-inside well-behaved pipelines. The floor is permissive for
     * the same reasons as the kClamp variant -- focal-outside and
     * focal-on-circle cells are still on the solid-color fallback.
     */
    @Test
    fun `ConicalGradients2ptInsideRepeatGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptInsideRepeatGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_inside_repeat")
                ?: error("original-888/gradients_2pt_conical_inside_repeat.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptInsideRepeatWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_inside_repeat-gpu")
            // Landing score 100.00% (kRepeat tiles produce byte-identical
            // output to the reference for the supported sub-cases). Floor
            // set well below to absorb future scoring drift.
            val floor = 95.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptInsideRepeatGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    /**
     * G4.4.2 cross-test : `ConicalGradients2ptInsideMirrorGM` -- same
     * grid as the kClamp variant above, with kMirror tile mode. Exercises
     * the new `fs_mirror` entry point of both the kRadial and the
     * focal-inside well-behaved pipelines.
     */
    @Test
    fun `ConicalGradients2ptInsideMirrorGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptInsideMirrorGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_inside_mirror")
                ?: error("original-888/gradients_2pt_conical_inside_mirror.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptInsideMirrorWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_inside_mirror-gpu")
            // Landing score 100.00% -- see Repeat variant above.
            val floor = 95.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptInsideMirrorGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
