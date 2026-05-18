package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientConicalDitherGM

/**
 * G-suivi (round 13) cross-test : `ShallowGradientConicalDitherGM` --
 * dither-on twin of the existing `ShallowGradientConicalNoDitherGM`
 * cross-test. 800 x 800 single drawRect filled with a kClamp
 * `SkConicalGradient` whose start and end circles share centre (400,
 * 400) -- the kRadial sub-case. Inner radius = 12.5 px (width / 64),
 * outer radius = 400 px (width / 2). Colours `0xFF555555 -> 0xFF444444`
 * (near-identical greys).
 *
 * Our rasterizer never applies dither, so GPU output is identical to
 * the no-dither sibling. The dithered upstream reference still matches
 * byte-exact on this near-grey ramp because the dither delta stays
 * sub-LSB.
 */
class ShallowGradientConicalDitherWebGpuTest {

    @Test
    fun `ShallowGradientConicalDitherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientConicalDitherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_conical")
                ?: error("original-888/shallow_gradient_conical.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientConicalDitherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_conical-gpu")
            // Landing score 100.00% (byte-exact match -- our rasterizer
            // never applies dither and the colour ramp is sub-LSB stable
            // through the present-pass colorspace transform).
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientConicalDitherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
