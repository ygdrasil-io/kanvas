package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.StrokesGM

/**
 * Cross-test : `StrokesGM` (`strokes_round`) on the GPU backend.
 *
 * 400 x 800, two horizontal panes (AA off / AA on) of `N = 50` random
 * stroked oval + roundrect pairs per pane under a 2-px-inset clipRect.
 * Paint defaults : `kStroke_Style`, `strokeWidth = 4.5`, `kRound_Cap`,
 * `kMiter_Join`. Pure axis-aligned (the rndRect helper consumes 6
 * rand calls per rect plus 1 for the colour ; 3 rects/iter, 2 drawn).
 *
 * Exercises `drawOval` + `drawRoundRect` stroking against clipRect.
 */
class StrokesWebGpuTest {

    @Test
    fun `StrokesGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 94.27 %. Residual drift on AA stroke
        // edges of ovals + roundrects under clipRect, where the
        // sub-pixel AA coverage convention drifts vs raster on small
        // rrects whose corner radii approach the stroke width.
        runGpuCrossTest(StrokesGM(), floor = 94.22)
    }
}
