package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConicalGradients2ptOutsideMirrorGM
import org.skia.tests.ConicalGradients2ptOutsideNoDitherGM
import org.skia.tests.ConicalGradients2ptOutsideRepeatGM

/**
 * G4.4.5 cross-test : `ConicalGradients2ptOutsideGM` -- 840 x 815 grid of
 * 4 columns x 5 rows of conical gradient rects. Four of the five makers
 * (`Outside` / `OutsideFlip` / `ZeroRadOutside` / `ZeroRadFlipOutside`)
 * produce focal-outside `kFocal` configurations -- now routed through
 * the focal-frame pipeline via the `subCase = 1` / `subCaseSign = +/-1`
 * uniform plumbing landed in G4.4.5. The fifth maker (`OutsideStrip`,
 * `r0 == r1`) is the kStrip sub-case (already routed since G4.4.4).
 *
 * The floor is permissive : the focal-outside masking (in_cone factor)
 * doesn't perfectly match the upstream AA edge behaviour around the
 * cone boundary, and dither is unimplemented. As the GPU dispatch
 * improves, the floor can be raised.
 */
class ConicalGradients2ptOutsideWebGpuTest {

    @Test
    fun `ConicalGradients2ptOutsideGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptOutsideNoDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_outside_nodither")
                ?: error("original-888/gradients_2pt_conical_outside_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptOutsideWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_outside_nodither-gpu")
            // Floor is permissive at the landing slice ; the focal-outside
            // routing now handles 4/5 makers but the cone-boundary masking
            // differs from upstream's analytic edge AA. Raise as the
            // dispatch improves.
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptOutsideGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    /**
     * G4.4.5 cross-test : `ConicalGradients2ptOutsideRepeatGM`. Same grid
     * as the kClamp variant with kRepeat tile mode -- exercises the
     * `fs_repeat` entry point of the focal-frame pipeline under the
     * focal-outside formula.
     */
    @Test
    fun `ConicalGradients2ptOutsideRepeatGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptOutsideRepeatGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_outside_repeat")
                ?: error("original-888/gradients_2pt_conical_outside_repeat.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptOutsideRepeatWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_outside_repeat-gpu")
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptOutsideRepeatGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    /**
     * G4.4.5 cross-test : `ConicalGradients2ptOutsideMirrorGM`. Mirror tile
     * mode variant.
     */
    @Test
    fun `ConicalGradients2ptOutsideMirrorGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptOutsideMirrorGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_outside_mirror")
                ?: error("original-888/gradients_2pt_conical_outside_mirror.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptOutsideMirrorWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_outside_mirror-gpu")
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptOutsideMirrorGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
