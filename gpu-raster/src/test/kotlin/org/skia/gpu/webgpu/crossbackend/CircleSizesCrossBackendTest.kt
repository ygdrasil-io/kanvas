package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CircleSizesGM

/**
 * Cross-backend test : `CircleSizesGM` on raster + GPU.
 *
 * Grid of AA-filled circles spanning a wide range of radii. Stresses
 * the circle / conic flattening at small + large sizes ; both backends
 * approximate the circle as flattened cubics or analytic ovals
 * depending on size, so this catches divergence at the threshold.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`CircleSizesTest`, tol=1) : 90.0 %
 *  - GPU (`CircleSizesWebGpuTest`, tol=8) : 96.89 %
 */
class CircleSizesCrossBackendTest {

    @Test
    fun `CircleSizesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = CircleSizesGM(),
            rasterFloor = 90.0,
            gpuFloor = 96.89,
            rasterTolerance = 1,
        )
    }
}
