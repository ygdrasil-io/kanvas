package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
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
        runGpuCrossTest(AnalyticAntialiasConvexGM(), floor = 99.85)
    }
}
