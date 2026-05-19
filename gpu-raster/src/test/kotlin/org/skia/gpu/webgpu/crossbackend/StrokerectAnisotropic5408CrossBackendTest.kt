package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokerectAnisotropic5408GM

/**
 * Cross-backend test : `StrokerectAnisotropic5408GM` on raster + GPU.
 *
 * 200 x 50 canvas, stroke-6 rect (5, 20, 15, 30) drawn under
 * `scale(10, 1)` -- 10:1 non-uniform but axis-aligned CTM. Regression
 * for crbug.com/skia/5408 : rect-stroker fast-path mishandled
 * non-square stroke widths. Output is a horizontally-stretched stroke
 * frame.
 *
 * Floors :
 *  - raster (tol=1) : 89.20 %
 *  - GPU (tol=8) : 89.20 %
 */
class StrokerectAnisotropic5408CrossBackendTest {

    @Test
    fun `StrokerectAnisotropic5408GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokerectAnisotropic5408GM(),
            rasterFloor = 89.15,
            gpuFloor = 89.15,
            rasterTolerance = 1,
        )
    }
}
