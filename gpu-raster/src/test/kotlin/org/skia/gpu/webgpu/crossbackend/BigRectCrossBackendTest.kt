package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BigRectGM

/**
 * Cross-backend test : `BigRectGM` on raster + GPU.
 *
 * 240+ rect draws across 8 paint configs (fill / stroke, w=0 / w=1, AA
 * on / off) and 3 size regimes (100, 5e10, 1e6). Exercises clipRect
 * (axis-aligned, integer — fast scissor path), translate, extreme
 * coordinates and the AA hairline + stroke corner edge cases.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`BigRectTest`, tol=1) : 95.0 %
 *  - GPU (`BigRectWebGpuTest`, tol=8) : 99.85 %
 */
class BigRectCrossBackendTest {

    @Test
    fun `BigRectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BigRectGM(),
            rasterFloor = 95.0,
            gpuFloor = 99.85,
            rasterTolerance = 1,
        )
    }
}
