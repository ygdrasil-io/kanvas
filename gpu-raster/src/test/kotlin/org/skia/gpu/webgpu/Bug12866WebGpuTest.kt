package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.Bug12866GM

/**
 * Cross-test : `Bug12866GM` on the GPU backend.
 *
 * Reproduces `skbug.com/40043963` : `SkStroker` recursion-limit issue
 * triggered by a giant `resScale` (1200). Renders the same tiny
 * quad-only closed contour twice — left side via `drawPath(path,
 * strokePaint)` at default `resScale = 1` (looks good), right side via
 * an explicit `SkStroker.fromPaint(paint, resScale = 1200).stroke(path)`
 * + fill (demonstrates the bug).
 *
 * G3.4.4 stroke coverage : exercises `SkStroker.resScale = 1200` —
 * extreme-scale subdivision stress for the stroker. Tiny 128 x 64
 * reference image, so per-pixel drift is amplified by the small total
 * pixel count.
 */
class Bug12866WebGpuTest {

    @Test
    fun `Bug12866GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = Bug12866GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bug12866")
                ?: error("original-888/bug12866.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[Bug12866WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "bug12866-gpu")
            // Score : 95.24 %. Tiny 128 x 64 reference image, so per-pixel
            // drift on the resScale=1200 right pane is amplified — most of
            // the loss comes from sub-pixel placement of the recursion-
            // bound-stressed outline.
            val floor = 95.19
            assertTrue(
                cmp.similarity >= floor,
                "Bug12866GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
