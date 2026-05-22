package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BitmapRectRoundingGM

/**
 * Cross-backend test : `BitmapRectRoundingGM` on raster + GPU.
 *
 * `drawBitmapRect` with sub-pixel `SkRect` destinations -- exercises
 * the rounding convention used to map fractional dst coordinates to
 * pixel cells. Both backends share the same `drawBitmapRect` fast-path
 * for integer-aligned bitmaps and are byte-exact against the reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class BitmapRectRoundingCrossBackendTest {

    @Test
    fun `BitmapRectRoundingGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BitmapRectRoundingGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
