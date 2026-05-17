package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.RgbwSweepGradientGM

/**
 * G4.3 cross-test : `RgbwSweepGradientGM` -- 100 x 100 canvas with a
 * single drawRect filled with a 4-hardstop `SkSweepGradient` (kClamp)
 * centred at (50, 50). The four sectors (white / blue / red / green)
 * line up with the +X / +Y / -X / -Y axes ; pure sweep-on-rect, no
 * CTM transform.
 *
 * Pure sweep-gradient-on-rect workout, all in-scope on GPU.
 */
class RgbwSweepGradientWebGpuTest {

    @Test
    fun `RgbwSweepGradientGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = RgbwSweepGradientGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("rgbw_sweep_gradient")
                ?: error("original-888/rgbw_sweep_gradient.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[RgbwSweepGradientWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "rgbw_sweep_gradient-gpu")
            val floor = 95.0
            assertTrue(
                cmp.similarity >= floor,
                "RgbwSweepGradientGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
