package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathSkbug11859GM

/**
 * Cross-backend test : `PathSkbug11859GM` on raster + GPU.
 *
 * Regression from skia bug 11859 — pathological path that previously
 * caused a divergence in one of the path arithmetic helpers. Both
 * backends should now agree on the rendered output.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`PathSkbug11859Test`, tol=1) : 90.0 %
 *  - GPU (`PathSkbug11859WebGpuTest`, tol=8) : 99.90 %
 */
class PathSkbug11859CrossBackendTest {

    @Test
    fun `PathSkbug11859GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathSkbug11859GM(),
            rasterFloor = 90.0,
            gpuFloor = 99.90,
            rasterTolerance = 1,
        )
    }
}
