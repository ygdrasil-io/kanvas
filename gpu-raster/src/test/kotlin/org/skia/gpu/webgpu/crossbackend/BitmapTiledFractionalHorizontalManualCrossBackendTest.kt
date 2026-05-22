package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BitmapTiledFractionalHorizontalManualGM

/**
 * Cross-backend test : `BitmapTiledFractionalHorizontalManualGM` on
 * raster + GPU.
 *
 * 1124 x 365 canvas, 10 strips of `drawImageRect` with sub-pixel
 * fractional `src` offsets straddling the `kBmpSmallTileSize == 1024`
 * boundary. Source bitmap 7168 x 1024, `eraseColor(SK_ColorWHITE)` so the
 * visual content is uniform white strips on black -- the regression check
 * is purely on the integer-pixel coverage of each strip's `dst` rect, no
 * sampling content. Exercises the `SkTiledImageUtils::DrawImageRect`
 * raster-fallback path (G5.1).
 *
 * Both backends land byte-exact (100.00 %). Floors mirror the existing
 * per-backend tests :
 *  - raster (`BitmapTiledTest`, tol=1) : 99.0 % (ratchet-only) ;
 *  - GPU (`BitmapTiledFractionalWebGpuTest`, tol=8) : 99.95 %.
 */
class BitmapTiledFractionalHorizontalManualCrossBackendTest {

    @Test
    fun `BitmapTiledFractionalHorizontalManualGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BitmapTiledFractionalHorizontalManualGM(),
            rasterFloor = 99.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
