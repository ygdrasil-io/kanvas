package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientSweepNoDitherGM

/**
 * Cross-test : `ShallowGradientSweepNoDitherGM` on the GPU backend.
 *
 * 800 x 800 single drawRect filled with a `SkSweepGradient` (kClamp tile
 * mode) centred at (400, 400). Colours `0xFF555555 -> 0xFF444444` (the
 * standard shallow-gradient near-grey ramp). Mirrors the linear / radial
 * shallow-gradient cross-tests, but routes through the sweep pipeline
 * (G4.3). Only the upstream reference differs from the dither variant
 * (dither off) -- the GM body is identical.
 */
class ShallowGradientSweepNoditherWebGpuTest {

    @Test
    fun `ShallowGradientSweepNoDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientSweepNoDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_sweep_nodither")
                ?: error("original-888/shallow_gradient_sweep_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientSweepNoditherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_sweep_nodither-gpu")
            // Ratchet : observed 100.00 % byte-exact. Floor pinned at
            // 99.95 % to absorb sub-LSB drift if future driver / pipeline
            // changes shift the gradient lerp arithmetic.
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientSweepNoDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
