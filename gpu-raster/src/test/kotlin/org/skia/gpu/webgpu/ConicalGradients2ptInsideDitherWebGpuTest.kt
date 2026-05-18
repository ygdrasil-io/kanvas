package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConicalGradients2ptInsideDitherGM

/**
 * G-suivi (round 13) cross-test : `ConicalGradients2ptInsideDitherGM`
 * -- dither-on twin of the existing `ConicalGradients2ptInsideGM`
 * (nodither) cross-test. 840 x 815 grid of 4 columns x 7 rows of
 * conical gradient rects, kClamp tile mode. All 28 cells place both
 * circle centres "inside" the cell, so the GPU paths exercise the
 * kRadial + focal-inside well-behaved sub-cases (G4.4.1 / G4.4.2).
 *
 * Our rasterizer never applies dither, so GPU output is identical to
 * the no-dither sibling. The dithered upstream reference still matches
 * closely because the dither delta is sub-LSB on most cells. Floor is
 * set permissively : focal-outside / focal-on-circle cells (Flip rows)
 * still fall through to solid-color machinery.
 */
class ConicalGradients2ptInsideDitherWebGpuTest {

    @Test
    fun `ConicalGradients2ptInsideDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConicalGradients2ptInsideDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_2pt_conical_inside")
                ?: error("original-888/gradients_2pt_conical_inside.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConicalGradients2ptInsideDitherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_2pt_conical_inside-gpu")
            // Landing score 97.51%. Higher than the nodither sibling
            // because the dither variant's reference happens to align
            // closer to the GPU's deterministic per-pixel output on the
            // cells where the focal sub-case still falls through to solid.
            val floor = 97.45
            assertTrue(
                cmp.similarity >= floor,
                "ConicalGradients2ptInsideDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
