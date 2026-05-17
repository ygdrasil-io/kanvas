package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.AnalyticAntialiasConvexGM

/**
 * Cross-test : `AnalyticAntialiasConvexGM` on the GPU backend.
 *
 * Five configurations stressing the analytic-AA convex-fill path under
 * a 1-degree rotated CTM : axis-aligned rect, ultra-thin rect + circle
 * row, degenerate cubic (crbug.com/662914), 4-vertex polygon hugging a
 * fractional boundary (skbug 40038820), and 10-px-wide tall vertical
 * strip on tile boundaries (skbug 40039068). First GM in scope using
 * `canvas.clear()` after G1.4 unblocked the bitmap-bypass on
 * non-raster devices.
 */
class AnalyticAntialiasConvexWebGpuTest {

    @Test
    fun `AnalyticAntialiasConvexGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = AnalyticAntialiasConvexGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("analytic_antialias_convex")
                ?: error("original-888/analytic_antialias_convex.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[AnalyticAntialiasConvexWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "analytic_antialias_convex-gpu")
            val floor = 99.85
            assertTrue(
                cmp.similarity >= floor,
                "AnalyticAntialiasConvexGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
