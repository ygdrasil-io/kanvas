package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathSkbug11886GM

/**
 * Cross-backend test : `PathSkbug11886GM` on raster + GPU.
 *
 * Regression from skia bug 11886 — companion case to skbug 11859.
 * Both backends should produce a matching small-but-specific output.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`PathSkbug11886Test`, tol=1) : 90.0 %
 *  - GPU (`PathSkbug11886WebGpuTest`, tol=8) : 99.56 %
 */
class PathSkbug11886CrossBackendTest {

    @Test
    fun `PathSkbug11886GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathSkbug11886GM(),
            rasterFloor = 90.0,
            gpuFloor = 99.56,
            rasterTolerance = 1,
        )
    }
}
