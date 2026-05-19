package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
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
        // 4 shapes × 4 scales × 2 AA modes. Score : 96.49 %. Drift
        // dominated by non-AA cells' binary coverage vs AA reference
        // and sub-pixel stroke-width-after-scale edge conventions on
        // the line / rect rows.
        runGpuCrossTest(ScaledStrokesGM(), floor = 96.44, logTag = "ScaledStrokesWebGpu")
    }
}
