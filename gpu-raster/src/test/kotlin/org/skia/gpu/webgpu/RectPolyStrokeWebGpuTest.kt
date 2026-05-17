package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.RectPolyStrokeGM

/**
 * Cross-test : `RectPolyStrokeGM` on the GPU backend.
 *
 * 3 (joins) x 4 (rect shapes including degenerate) x 2 (rotations) x 2
 * (procs : drawRect vs drawPath) = 48 cells of stroked rects at
 * `strokeWidth = 20`. Each cell additionally lays a 0-width green
 * hairline overlay to expose any geometry mismatch between the dedicated
 * rect rasterizer and the general path rasterizer.
 *
 * G3.4.4 joins coverage : every join kind (miter / round / bevel) cycled
 * across rotated rect paths. The drawPath proc lands on G3.4.1
 * SkStroker integration ; the drawRect proc lands on the dedicated
 * stroke-rect fast path (G3.1) — divergence between the two procs shows
 * up as red/black smear in the rendered cells.
 */
class RectPolyStrokeWebGpuTest {

    @Test
    fun `RectPolyStrokeGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = RectPolyStrokeGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("rect_poly_stroke")
                ?: error("original-888/rect_poly_stroke.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[RectPolyStrokeWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "rect_poly_stroke-gpu")
            // Score : 98.21 %. 48 cells x 2 procs each — divergence
            // dominated by the green-hairline overlay's AA edge across
            // every rotated cell, and by the dedicated rect rasterizer vs
            // path-stroker emission on the degenerate-rect rows.
            val floor = 98.16
            assertTrue(
                cmp.similarity >= floor,
                "RectPolyStrokeGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
