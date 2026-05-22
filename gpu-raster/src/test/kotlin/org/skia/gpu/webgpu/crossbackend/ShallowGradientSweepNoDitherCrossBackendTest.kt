package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientSweepNoDitherGM

/**
 * Cross-backend test : `ShallowGradientSweepNoDitherGM`
 * (`shallow_gradient_sweep_nodither`) on raster + GPU.
 *
 * 800 x 800 single `drawRect` filled with a `SkSweepGradient` (kClamp
 * tile mode) centred at (400, 400). Colours `0xFF555555 -> 0xFF444444`
 * (the standard shallow-gradient near-grey ramp). Routes through the
 * sweep pipeline (G4.3) ; mirrors the linear / radial shallow-gradient
 * cross-tests on the sweep variant.
 *
 * Both backends land byte-exact (100.00 %). Floors :
 *  - raster (`Round9Test`, tol=1) : 99.95 % ;
 *  - GPU (`ShallowGradientSweepNoditherWebGpuTest`, tol=8) : 99.95 %.
 */
class ShallowGradientSweepNoDitherCrossBackendTest {

    @Test
    fun `ShallowGradientSweepNoDitherGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientSweepNoDitherGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
