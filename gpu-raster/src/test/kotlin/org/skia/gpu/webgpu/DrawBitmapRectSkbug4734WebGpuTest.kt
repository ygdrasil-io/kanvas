package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.DrawBitmapRectSkbug4734GM

/**
 * Cross-test : `DrawBitmapRectSkbug4734GM` on the GPU backend.
 *
 * Single `drawImageRect` of `images/randPixels.png` (8 x 8 random
 * pixels) with a sub-pixel `src` inset of `(0.5, 1.5)` mapped through
 * `Matrix::Scale(8, 8)` to produce the destination rect. Regression
 * check for fractional-`src` precision in `drawImageRect` under an
 * axis-aligned scale — the G5.1 / G5.1.1 / G5.3 pipeline handles this
 * via the in-shader sub-rect masking against the half-pixel-snapped
 * src bounds.
 *
 * 64 x 64 canvas. No CTM rotation, no shader local matrix beyond
 * what the routing derives from `(src, dst)`. Landing score 100.00 %
 * (byte-exact). Floor 99.95 % to absorb scoring drift.
 */
class DrawBitmapRectSkbug4734WebGpuTest {

    @Test
    fun `DrawBitmapRectSkbug4734GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(DrawBitmapRectSkbug4734GM(), floor = 99.95)
    }
}
