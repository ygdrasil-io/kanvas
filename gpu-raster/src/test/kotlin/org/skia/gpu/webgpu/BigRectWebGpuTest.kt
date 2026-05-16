package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.BigRectGM

/**
 * G3.1 cross-test — BigRectGM on GPU.
 *
 * BigRectGM was originally the named G1 target but was deferred past
 * G2.3 because stroke (G3.1, this slice) was the blocker. With stroke
 * landed, the GM should produce a meaningful score.
 *
 * What BigRectGM exercises that the existing tests don't :
 *  - **Stroke** (rect strokes with widths 0 and 1)
 *  - **AA on/off** mixed across 8 paint variations
 *  - **`clipRect`** (axis-aligned, integer — fast scissor path)
 *  - **`translate`** in the CTM (transparent to the device)
 *  - **Extreme coordinates** (5e10, 1e6) — tests that pixelEdge math
 *    and scissor clamping don't blow up on huge inputs
 *  - **240+ draws per call** (8 paint configs × 3 sizes × 10 rects)
 *
 * The reference PNG comes from upstream Skia's DM, encoded in
 * Rec.2020. Same colorspace drift caveat as ThinRectsGM (D3 / G6).
 */
class BigRectWebGpuTest {

    @Test
    fun `BigRectGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BigRectGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bigrect")
                ?: error("original-888/bigrect.png missing from test classpath")

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[BigRectWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "bigrect-gpu")

            // G3.1 baseline floor : 70.5%. Significantly below the plan's
            // G2 90% target for the 4 cibles GMs, but a useful starting
            // point. Major drift sources still to address :
            //  - AA hairline approximated as non-AA (Skia's AA hairline
            //    distributes coverage between adjacent pixel rows/cols).
            //    A real fix needs sub-pixel hairline coverage.
            //  - Stroke-AA corner pixels : my 4-edge decomposition paints
            //    them through top/bottom only, while raster's strokeRectAA
            //    computes coverage as (1 - cov_inside) which yields slightly
            //    different fractional values at corners.
            //  - Persistent colorspace drift (sRGB vs Rec.2020), to be
            //    resolved in G6.
            // G6.0 colorspace transform → near-perfect (40/40625 pixels still
            // off, AA hairline + stroke corner edge cases for G3.4).
            val floor = 99.85
            assertTrue(
                cmp.similarity >= floor,
                "BigRectGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. See build/debug-images/bigrect-gpu.png.",
            )
        }
    }
}
