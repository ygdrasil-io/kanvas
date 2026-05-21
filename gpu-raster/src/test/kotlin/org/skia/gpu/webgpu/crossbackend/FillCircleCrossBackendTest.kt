package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.FillCircleGM

/**
 * Cross-backend test : `FillCircleGM` on raster + GPU.
 *
 * AA-filled circle of moderate radius. The simplest possible
 * convex-circle exercise — meant as a sanity check for the
 * convex-fill path on both backends.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`FillCircleTest`, tol=1) : 85.0 %
 *  - GPU (`FillCircleWebGpuTest`, tol=8) : 98.50 %
 */
class FillCircleCrossBackendTest {

    @Test
    fun `FillCircleGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = FillCircleGM(),
            rasterFloor = 85.0,
            gpuFloor = 98.50,
            rasterTolerance = 1,
        )
    }
}
