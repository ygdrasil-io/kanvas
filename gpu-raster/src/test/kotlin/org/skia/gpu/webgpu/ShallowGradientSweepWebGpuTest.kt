package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientSweepDitherGM

/**
 * Cross-test : `ShallowGradientSweepDitherGM` on the GPU backend.
 *
 * Same body as the no-dither variant -- 800 x 800 single drawRect filled
 * with a kClamp `SkSweepGradient` from `0xFF555555 -> 0xFF444444` centred
 * at (400, 400). Only the upstream reference differs (dither on). Our
 * rasterizer never applies dither so the GPU output is identical to the
 * no-dither path ; this test exposes the dither-induced drift between
 * our undithered output and the dithered reference.
 */
class ShallowGradientSweepWebGpuTest {

    @Test
    fun `ShallowGradientSweepDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientSweepDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_sweep")
                ?: error("original-888/shallow_gradient_sweep.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientSweepWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_sweep-gpu")
            // Ratchet : observed 100.00 % byte-exact (the dither delta
            // stays sub-LSB on this near-grey ramp, even against the
            // dithered reference). Floor pinned at 99.95 %.
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientSweepDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
