package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.StrokeRectsGM

/**
 * Cross-test : `StrokeRectsGM` on the GPU backend (non-rotated variant).
 *
 * 800 x 800, 2 x 2 panes of `N = 100` random stroked rects each under
 * a 2-px inset clipRect. The 4 panes cover (AA off / AA on) x
 * (strokeWidth 0 / 3). Each pane reseeds a fresh `SkRandom`, so all
 * 4 panes draw the same 100 rect geometries but with different paint
 * modes. The rndRect helper produces some degenerate negative-extent
 * rects which exercise the stroker / 4-edge decomposition fall-backs.
 *
 * Pure axis-aligned (no rotate / skew / shader).
 */
class StrokeRectsWebGpuTest {

    @Test
    fun `StrokeRectsGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 99.86 %. Residual drift on AA stroke
        // edges of degenerate negative-extent rects from rndRect's
        // offset arithmetic, where the AA coverage convention differs
        // sub-LSB from raster.
        runGpuCrossTest(StrokeRectsGM(), floor = 99.81)
    }
}
