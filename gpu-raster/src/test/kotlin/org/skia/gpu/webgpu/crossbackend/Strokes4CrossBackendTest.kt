package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Strokes4GM

/**
 * Cross-backend test : `Strokes4GM` on raster + GPU.
 *
 * One stroked circle at `(0, 2)` with radius `1.97` and stroke width
 * `0.055`, under a 1000x uniform CTM scale. Net device-space geometry
 * is a stroke ~55 px wide on a circle of radius ~1970 px centred at
 * `(0, 2000)` -- only the bottom-of-canvas slice falls inside the
 * 400 x 800 viewport, producing a thick horizontal strip.
 *
 * Stresses the stroker under large CTM scale -- the inverse of
 * StrokeCircleGM at 20x.
 *
 * Floors :
 *  - raster (tol=1) : 99.94 %
 *  - GPU (tol=8) : 99.94 %
 */
class Strokes4CrossBackendTest {

    @Test
    fun `Strokes4GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Strokes4GM(),
            rasterFloor = 99.94,
            gpuFloor = 99.94,
            rasterTolerance = 1,
        )
    }
}
