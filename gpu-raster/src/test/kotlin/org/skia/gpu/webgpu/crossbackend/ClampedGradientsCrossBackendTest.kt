package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClampedGradientsGM

/**
 * Cross-backend test : `ClampedGradientsGM` on raster + GPU.
 *
 * Exercises gradient color clamping behaviour past the [0, 1] interval.
 * Both backends should land on identical sampled values inside the
 * clamp regions ; raster routes through the analytic gradient
 * evaluator while GPU goes through the fragment shader emitter.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ClampedGradientsTest`, tol=1) : 93.0 %
 *  - GPU (`ClampedGradientsWebGpuTest`, tol=8) : 99.95 %
 */
class ClampedGradientsCrossBackendTest {

    @Test
    fun `ClampedGradientsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClampedGradientsGM(),
            rasterFloor = 93.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
