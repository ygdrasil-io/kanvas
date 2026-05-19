package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
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
        // Non-AA multi-contour kWinding fill via stencil-and-cover
        // (G3.3b.2b) → 99.99 % (~70 edge pixels drift, sub-channel,
        // caused by reference being AA-rasterised while our fill is
        // binary coverage — closed by G3.3b.3 AA stencil-and-cover).
        runGpuCrossTest(Bug7792GM(), floor = 99.94)
    }
}
