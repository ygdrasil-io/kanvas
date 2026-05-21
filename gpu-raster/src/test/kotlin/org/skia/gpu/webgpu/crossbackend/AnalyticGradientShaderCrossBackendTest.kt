package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnalyticGradientShaderGM

/**
 * Cross-backend test : `AnalyticGradientShaderGM` on raster + GPU.
 *
 * Exercises the analytic linear-gradient fast path. The raster floor
 * is wide (60 %) because the GM intentionally probes the boundary
 * where the analytic shader takes over from generic gradient sampling,
 * and small floating-point drift accumulates across the swatch.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`AnalyticGradientShaderTest`, tol=1) : 60.0 %
 *  - GPU (`AnalyticGradientShaderWebGpuTest`, tol=8) : 99.95 %
 */
class AnalyticGradientShaderCrossBackendTest {

    @Test
    fun `AnalyticGradientShaderGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = AnalyticGradientShaderGM(),
            rasterFloor = 60.0,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
