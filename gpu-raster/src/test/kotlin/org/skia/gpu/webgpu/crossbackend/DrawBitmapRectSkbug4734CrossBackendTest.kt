package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.DrawBitmapRectSkbug4734GM

/**
 * Cross-backend test : `DrawBitmapRectSkbug4734GM` on raster + GPU.
 *
 * Reduction from skbug 4734 -- `drawBitmapRect` with a destination rect
 * that falls on a half-pixel boundary, exercising the pixel-snap fast
 * path. Both backends byte-exact against the reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class DrawBitmapRectSkbug4734CrossBackendTest {

    @Test
    fun `DrawBitmapRectSkbug4734GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = DrawBitmapRectSkbug4734GM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
