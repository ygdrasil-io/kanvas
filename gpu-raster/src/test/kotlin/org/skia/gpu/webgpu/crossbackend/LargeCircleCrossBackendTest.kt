package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.LargeCircleGM

/**
 * Cross-backend test : `LargeCircleGM` on raster + GPU.
 *
 * 250 x 250 canvas, one anti-aliased stroked circle at
 * `center = (1052.5, 506.9)` `radius = 1096.7` -- only a tiny slice
 * of the circle's arc lands in viewport. Regression test for biased
 * AA coverage at huge radii (conicpaths.cpp `largecircle`). Pure G3.4
 * stroker on conic path at large radius.
 *
 * Floors :
 *  - raster (tol=1) : 99.07 %
 *  - GPU (tol=8) : 99.17 %
 */
class LargeCircleCrossBackendTest {

    @Test
    fun `LargeCircleGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = LargeCircleGM(),
            rasterFloor = 99.02,
            gpuFloor = 99.12,
            rasterTolerance = 1,
        )
    }
}
