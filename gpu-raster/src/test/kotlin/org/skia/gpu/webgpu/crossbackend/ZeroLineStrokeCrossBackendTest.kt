package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ZeroLineStrokeGM

/**
 * Cross-backend test : `ZeroLineStrokeGM` on raster + GPU.
 *
 * Zero-length line strokes — degenerate path where the stroke
 * geometry collapses to a single cap-shaped dot. Both backends
 * have to agree on the dot pixel layout, which is more subtle than
 * it looks (cap-only outline routing).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ZeroLineStrokeTest`, tol=1) : 85.0 %
 *  - GPU (`ZeroLineStrokeWebGpuTest`, tol=8) : 94.00 %
 */
class ZeroLineStrokeCrossBackendTest {

    @Test
    fun `ZeroLineStrokeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ZeroLineStrokeGM(),
            rasterFloor = 85.0,
            gpuFloor = 94.00,
            rasterTolerance = 1,
        )
    }
}
