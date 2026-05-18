package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConicalGradients2ptEdgeMirrorGM
import org.skia.tests.ConicalGradients2ptEdgeNoDitherGM
import org.skia.tests.ConicalGradients2ptEdgeRepeatGM

/**
 * G4.4.6 cross-test : `ConicalGradients2ptEdgeGM` -- 840 x 815 grid of
 * conical gradient rects exercising the 7 "edge" makers (EdgeX / EdgeY /
 * ZeroRadEdgeX / ZeroRadEdgeY / TouchX / TouchY / InsideSmallRad).
 * The `TouchX` / `TouchY` makers produce focal-on-circle configurations
 * (centres positioned so that `dCenter = r1 - r0`, i.e. start circle is
 * tangent to the end circle at the focal point) -- now routed through
 * the focal-frame pipeline via the `subCase = 2` plumbing landed in
 * G4.4.6. The other makers route through existing pipelines :
 *   - EdgeX / EdgeY : focal-inside well-behaved (G4.4.1) ;
 *   - ZeroRadEdgeX / ZeroRadEdgeY : focal-inside (r0 = 0, well-behaved) ;
 *   - InsideSmallRad : kRadial (G4.4).
 *
 * The floor mirrors the focal-outside cross-tests (90 %) ; the analytic
 * cone-boundary masking near the tangent point differs slightly from
 * upstream's AA edge handling, and dither is unimplemented. Raise as
 * the dispatch coverage improves.
 */
class ConicalGradients2ptEdgeWebGpuTest {

    @Test
    fun `ConicalGradients2ptEdgeNoDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptEdgeNoDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_edge_nodither")
                ?: error("original-888/gradients_2pt_conical_edge_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptEdgeWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_edge_nodither-gpu")
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptEdgeNoDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    @Test
    fun `ConicalGradients2ptEdgeRepeatGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptEdgeRepeatGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_edge_repeat")
                ?: error("original-888/gradients_2pt_conical_edge_repeat.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptEdgeRepeatWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_edge_repeat-gpu")
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptEdgeRepeatGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    @Test
    fun `ConicalGradients2ptEdgeMirrorGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptEdgeMirrorGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_edge_mirror")
                ?: error("original-888/gradients_2pt_conical_edge_mirror.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptEdgeMirrorWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_edge_mirror-gpu")
            val floor = 90.00
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptEdgeMirrorGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
