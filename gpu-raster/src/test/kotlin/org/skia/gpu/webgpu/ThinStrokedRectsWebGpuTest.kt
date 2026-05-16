package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ThinStrokedRectsGM

/**
 * Cross-test : `ThinStrokedRectsGM` on the GPU backend.
 *
 * 7 columns × 8 rows of axis-aligned stroked rects with sub-pixel
 * stroke widths (`4, 2, 1, 0.5, 0.25, 0.125, 0`). Every paint is
 * `isAntiAlias = true` — the G3.1 4-edge decomposition routes each
 * stroke edge through `drawFillRect` which honours AA via the
 * G2.3a coverage shader. The smallest widths exercise the
 * fractional-bounds path heavily.
 */
class ThinStrokedRectsWebGpuTest {

    @Test
    fun `ThinStrokedRectsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ThinStrokedRectsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("thinstrokedrects")
                ?: error("original-888/thinstrokedrects.png missing from test classpath")

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ThinStrokedRectsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "thinstrokedrects-gpu")

            // Floor calibrated by the baseline 87.13%. Sub-pixel strokes
            // (widths down to 0.125) exercise the AA coverage shader
            // extensively. Drift profile : sRGB vs Rec.2020 colorspace +
            // sub-pixel stroke edge conventions vs raster's strokeRectAA.
            // Below the G2 90% target ; expected to climb with G6
            // (colorspace) + AA stroke corner fixes (G3.4).
            val floor = 87.0
            assertTrue(
                cmp.similarity >= floor,
                "ThinStrokedRectsGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. See build/debug-images/thinstrokedrects-gpu.png.",
            )
        }
    }
}
