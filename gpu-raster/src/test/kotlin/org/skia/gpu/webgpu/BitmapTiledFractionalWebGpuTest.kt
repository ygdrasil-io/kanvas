package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BitmapTiledFractionalHorizontalManualGM

/**
 * Cross-test : `BitmapTiledFractionalHorizontalManualGM` on the GPU
 * backend.
 *
 * 10 strips of `drawImageRect` with sub-pixel fractional `src` offsets
 * straddling the `kBmpSmallTileSize == 1024` boundary, exercising the
 * `SkTiledImageUtils::DrawImageRect` raster-fallback path (the source
 * bitmap is `eraseColor(SK_ColorWHITE)` so the visual content is
 * uniform white strips on a black background — the regression check is
 * purely on the integer-pixel coverage of each strip's `dst` rect, no
 * sampling content).
 *
 * 1124 x 365 canvas, horizontal strips, source bitmap 7168 x 1024. All
 * draws are axis-aligned (`drawImageRect(image, src, dst, ...,
 * kStrict)`) ; no CTM rotation, no shader local matrix. Pure G5.1
 * pipeline workout.
 *
 * Lands at 100.00 % byte-exact ; floor 99.95 % absorbs scoring drift.
 * The Vertical sibling is the transpose of Horizontal and would test
 * the same pipeline rotated 90 degrees — left to a future round if a
 * regression-relevant divergence appears.
 */
class BitmapTiledFractionalWebGpuTest {

    @Test
    fun `BitmapTiledFractionalHorizontalManualGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(BitmapTiledFractionalHorizontalManualGM(), floor = 99.95)
    }
}
