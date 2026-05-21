package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.TestGradientGM

/**
 * Cross-backend test : `TestGradientGM` on raster + GPU.
 *
 * Sanity-check gradient case — a single rect filled by a small linear
 * gradient with a handful of colour stops. Both backends should land
 * very close to byte-exact ; the floor catches regressions in the
 * shared stop-interpolation code.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`TestGradientTest`, tol=1) : 90.0 %
 *  - GPU (`TestGradientWebGpuTest`, tol=8) : 99.90 %
 */
class TestGradientCrossBackendTest {

    @Test
    fun `TestGradientGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = TestGradientGM(),
            rasterFloor = 90.0,
            gpuFloor = 99.90,
            rasterTolerance = 1,
        )
    }
}
