package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.StrokeRectGM

/**
 * Cross-test : `StrokeRectGM` on the GPU backend.
 *
 * 1400 x 740 ; 12 cols x 6 rows of stroked rects across three joins
 * (miter, round, bevel) and twelve geometry variants (normal, inverted,
 * smaller-than-stroke, zero-extent, `FLT_EPSILON`-thin), styles stroke
 * and stroke-and-fill. Each cell layers `drawRect(rect, paint)`, the
 * stroker red-outline path `drawPath(stroker.stroke(SkPath.Rect(r)))`,
 * and the path's landmark control points via `drawPoints(kPoints_Mode)`.
 *
 * Previously panicked in `wgpu::setVertexBuffer("invalid size")` on the
 * zero-extent / FLT_EPSILON cells because the stroker output is multi-
 * contour with each contour carrying < 3 vertices, which makes
 * `fanTessellateContours` emit an empty triangle list (n >= 3 lets the
 * early guard through, but each individual contour fan needs >= 3
 * vertices). Fixed by filtering empty-vertex stencil-and-cover draws
 * out of `pending` before resource allocation.
 */
class StrokeRectWebGpuTest {

    @Test
    fun `StrokeRectGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = StrokeRectGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("strokerect")
                ?: error("original-888/strokerect.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[StrokeRectWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "strokerect-gpu")
            // Observed 95.96 % post-fix on macOS / Apple M2 / WebGPU
            // (wgpu 27.0.4.0). Residual drift on sub-pixel stroke edges
            // of the degenerate / `FLT_EPSILON` cells and the landmark
            // dot rendering ; the wide gray stroke + red outline path
            // dominate the score (the panic-blocked rows now match
            // pixel-for-pixel on the normal geometry cells).
            val floor = 95.91
            assertTrue(
                cmp.similarity >= floor,
                "StrokeRectGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
