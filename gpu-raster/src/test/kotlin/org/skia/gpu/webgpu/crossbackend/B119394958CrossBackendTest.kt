package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.B119394958GM

/**
 * Cross-backend test : `B119394958GM` on raster + GPU.
 *
 * Regression case from chromium b/119394958 — a contour that requires
 * the path-flattener to behave correctly near the curve degeneracy
 * threshold. Both backends should agree on the same flattened pixels.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`B119394958Test`, tol=1) : 88.0 %
 *  - GPU (`B119394958WebGpuTest`, tol=8) : 93.74 %
 */
class B119394958CrossBackendTest {

    @Test
    fun `B119394958GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = B119394958GM(),
            rasterFloor = 88.0,
            gpuFloor = 93.74,
            rasterTolerance = 1,
        )
    }
}
