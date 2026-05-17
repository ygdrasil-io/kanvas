package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ScaledStrokesGM

/**
 * Cross-test : `ScaledStrokesGM` on the GPU backend.
 *
 * 4 × 4 cells (4 scales × 4 shapes) × 2 panes (no-AA / AA) — each cell
 * draws a "rounded square" (4 cubic Beziers), a circle, a rectangle, or
 * a line. Under `scale(s, s)` the paint sets `strokeWidth = 4 / scale`,
 * so each cell shows a stroke of the same nominal device-space width
 * regardless of scale.
 *
 * G3.4.1 stroke coverage : exercises the resScale code path under four
 * shape kinds (cubic-Bezier path, circle, rect, line) at scales
 * 1×/2×/3×/4×, in both non-AA and AA passes.
 */
class ScaledStrokesWebGpuTest {

    @Test
    fun `ScaledStrokesGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ScaledStrokesGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("scaledstrokes")
                ?: error("original-888/scaledstrokes.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ScaledStrokesWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "scaledstrokes-gpu")
            // 4 shapes × 4 scales × 2 AA modes. Score : 96.49 %. Drift
            // dominated by non-AA cells' binary coverage vs AA reference
            // and sub-pixel stroke-width-after-scale edge conventions on
            // the line / rect rows.
            val floor = 96.44
            assertTrue(
                cmp.similarity >= floor,
                "ScaledStrokesGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
