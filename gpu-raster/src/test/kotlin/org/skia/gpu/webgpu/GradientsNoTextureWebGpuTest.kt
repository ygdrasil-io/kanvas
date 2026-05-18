package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.GradientsNoTextureGM

/**
 * G-suivi (round 15) cross-test : `GradientsNoTextureGM` -- 640 x 615
 * grid of 4 x 5 x 2 cells of 50 x 50 axis-aligned drawRects. The grid
 * iterates :
 *  - 4 colour-stop configurations (1 / 2 / 3 / 4 stops from `{red,
 *    green, blue, white}`) ;
 *  - 5 gradient kinds (linear, radial, sweep, 2pt-radial = conical
 *    focal-inside, 2pt-conical = conical focal-outside) ;
 *  - 2 alpha values (`0xFF`, `0x40`).
 *
 * All 5 gradient kinds are in scope on the GPU :
 *  - linear (G4.0), radial (G4.1), sweep (G4.3) since their kClamp
 *    landing slices ;
 *  - conical focal-inside since G4.4.1 ;
 *  - conical focal-outside since G4.4.5.
 *
 * The 1-stop column is fully solid (single colour) but still routes
 * through the gradient shader, exercising the colorizer's
 * degenerate-stop path. The 0x40-alpha half-rows exercise the paint
 * alpha modulation post-pass. Drift concentrates on a few LSB on the
 * focal-outside cone-boundary edges (G4.4.5 in_cone masking differs
 * slightly from upstream AA) and on the alpha-modulated halves where
 * premul rounding compounds.
 *
 * Dither is a no-op in our pipeline (16-bpc working space). Both
 * upstream variants (`gradients_no_texture` and
 * `gradients_no_texture_nodither`) render identically through us ; the
 * upstream PNGs only differ in the sub-LSB dither pattern, so both
 * match at the same similarity.
 */
class GradientsNoTextureWebGpuTest {

    @Test
    fun `GradientsNoTextureGM dither renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = GradientsNoTextureGM(dither = true)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_no_texture")
                ?: error("original-888/gradients_no_texture.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[GradientsNoTextureDitherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_no_texture-gpu")
            // Landing score 88.33 %. Floor set 0.05 % below for scoring
            // drift headroom.
            val floor = 88.25
            assertTrue(
                cmp.similarity >= floor,
                "GradientsNoTextureGM (dither) regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }

    @Test
    fun `GradientsNoTextureGM nodither renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = GradientsNoTextureGM(dither = false)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("gradients_no_texture_nodither")
                ?: error("original-888/gradients_no_texture_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[GradientsNoTextureNoDitherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "gradients_no_texture_nodither-gpu")
            // Landing score 88.33 %. Floor set 0.05 % below for scoring
            // drift headroom.
            val floor = 88.25
            assertTrue(
                cmp.similarity >= floor,
                "GradientsNoTextureGM (nodither) regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
