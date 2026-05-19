package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
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
        // Score : 95.24 %. Tiny 128 x 64 reference image, so per-pixel
        // drift on the resScale=1200 right pane is amplified — most of
        // the loss comes from sub-pixel placement of the recursion-
        // bound-stressed outline.
        runGpuCrossTest(Bug12866GM(), floor = 95.19)
    }
}
