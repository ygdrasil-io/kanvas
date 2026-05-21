package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConcavePathsGM

/**
 * Cross-backend test : `ConcavePathsGM` on raster + GPU.
 *
 * Grid of concave polygon fills (stars, gears, self-intersecting
 * shapes). Routes through stencil-and-cover (GPU) and the scanline
 * fill (raster) — exactly the cross-validation point we care about
 * for the AA stencil dispatch.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ConcavePathsTest`, tol=1) : 98.0 %
 *  - GPU (`ConcavePathsWebGpuTest`, tol=8) : 99.25 %
 */
class ConcavePathsCrossBackendTest {

    @Test
    fun `ConcavePathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ConcavePathsGM(),
            rasterFloor = 98.0,
            gpuFloor = 99.25,
            rasterTolerance = 1,
        )
    }
}
