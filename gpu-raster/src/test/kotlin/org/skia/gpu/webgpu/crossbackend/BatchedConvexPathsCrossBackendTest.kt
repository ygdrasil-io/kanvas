package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BatchedConvexPathsGM

/**
 * Cross-backend test : `BatchedConvexPathsGM` on raster + GPU.
 *
 * Many small convex paths drawn back-to-back. Stresses the convex-fill
 * path on the GPU side and the analytic fast path on raster ; the
 * raster floor is wide because the GM packs many AA edges and minor
 * AA-edge convention drift accumulates.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`BatchedConvexPathsTest`, tol=1) : 30.0 %
 *  - GPU (`BatchedConvexPathsWebGpuTest`, tol=8) : 99.85 %
 */
class BatchedConvexPathsCrossBackendTest {

    @Test
    fun `BatchedConvexPathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BatchedConvexPathsGM(),
            rasterFloor = 30.0,
            gpuFloor = 99.85,
            rasterTolerance = 1,
        )
    }
}
