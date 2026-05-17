package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientConicalNoDitherGM

/**
 * G4.4 cross-test : `ShallowGradientConicalNoDitherGM` -- 800 x 800
 * single drawRect filled with a kClamp `SkConicalGradient` whose start
 * and end circles share a centre at (400, 400) -- the kRadial sub-case.
 * Inner radius = 12.5 px (width / 64), outer radius = 400 px (width / 2).
 * Colours `0xFF555555 -> 0xFF444444` (near-identical greys).
 *
 * Cross-test reference is the upstream Skia raster output (8-bit, no
 * dither). Because SkConicalGradient.Make collapses this to the kRadial
 * sub-case, the GPU pipeline's
 *   t = (length(p - c1) - r0) / (r1 - r0)
 * formula matches the CPU port byte-for-byte (modulo the 8-bit storage +
 * present-pass colorspace transform).
 */
class ShallowGradientConicalNoditherWebGpuTest {

    @Test
    fun `ShallowGradientConicalNoDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientConicalNoDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_conical_nodither")
                ?: error("original-888/shallow_gradient_conical_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientConicalNoditherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_conical_nodither-gpu")
            val floor = 99.50
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientConicalNoDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
