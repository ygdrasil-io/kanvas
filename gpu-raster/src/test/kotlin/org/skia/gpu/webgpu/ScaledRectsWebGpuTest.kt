package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ScaledRectsGM

/**
 * G3.3a.1 cross-test — ScaledRectsGM on GPU.
 *
 * The 4th G2 cible. Exercises a stack of features that this codebase
 * recently unlocked piece by piece :
 *  - `drawPaint` with a non-trivial bg colour (G3.2)
 *  - `clipRect` on an axis-aligned integer rect (G1.2 scissor path)
 *  - `setMatrix` with rotated/skewed CTM (SkCanvas re-routes drawRect
 *    through drawPath when the CTM is non-axis-aligned ; that drawPath
 *    -> 4-vertex polygon path is what G3.3a unlocked)
 *  - `kPlus` blend mode (G3.3a.1, this slice)
 *
 * Expected drift sources :
 *  - sRGB vs Rec.2020 colorspace (resolved in G6)
 *  - AA fractional coverage on edges of the rotated parallelograms
 *    (G3.3a is non-AA polygon fill ; AA polygon coverage = G3.3b)
 *  - Pixel-edge inclusion convention at parallelogram boundaries
 *    (the fan tessellation paints a convex polygon, but the exact
 *    fill-rule may diverge from raster's scanline rasterizer at
 *    integer-edge pixels)
 */
class ScaledRectsWebGpuTest {

    @Test
    fun `ScaledRectsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ScaledRectsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("scaledrects")
                ?: error("original-888/scaledrects.png missing from test classpath")

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ScaledRectsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "scaledrects-gpu")

            // Floor calibrated by the G3.3a.1 baseline 87.79% : in the
            // same band as BigRectGM (drift profile = sRGB vs Rec.2020 +
            // edge convention on the rotated parallelograms + no AA
            // polygon coverage yet). Sits below the plan's G2 90% target,
            // unblocked when G3.3b lands AA polygon coverage and G6 the
            // working-space convergence.
            // G6.0 colorspace transform → perfect match.
            val floor = 99.99
            assertTrue(
                cmp.similarity >= floor,
                "ScaledRectsGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. See build/debug-images/scaledrects-gpu.png.",
            )
        }
    }
}
