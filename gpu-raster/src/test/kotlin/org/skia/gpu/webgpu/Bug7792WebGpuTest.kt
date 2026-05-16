package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.Bug7792GM

/**
 * Cross-test : `Bug7792GM` on the GPU backend.
 *
 * 16 line-only paths exercising `moveTo`/`close` edge cases for the
 * non-AA fill rasterizer (skbug.com/40039046 reductions). Each path is
 * a small variation of "rect with extra moveTo or duplicate close" —
 * many are multi-contour with degenerate sub-contours. Default
 * `SkPaint` = `kWinding` fill, non-AA — exercises G3.3b.2b
 * stencil-and-cover multi-contour fill exclusively.
 */
class Bug7792WebGpuTest {

    @Test
    fun `Bug7792GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = Bug7792GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bug7792")
                ?: error("original-888/bug7792.png missing from test classpath")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[Bug7792WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "bug7792-gpu")
            // Non-AA multi-contour kWinding fill via stencil-and-cover
            // (G3.3b.2b) → 99.99 % (~70 edge pixels drift, sub-channel,
            // caused by reference being AA-rasterised while our fill is
            // binary coverage — closed by G3.3b.3 AA stencil-and-cover).
            val floor = 99.94
            assertTrue(
                cmp.similarity >= floor,
                "Bug7792GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
