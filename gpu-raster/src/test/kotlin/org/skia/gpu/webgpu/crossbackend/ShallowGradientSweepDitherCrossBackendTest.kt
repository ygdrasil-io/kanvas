package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientSweepDitherGM

/**
 * Cross-backend test : `ShallowGradientSweepDitherGM`
 * (`shallow_gradient_sweep`) on raster + GPU.
 *
 * 800 x 800 single `drawRect` filled with a kClamp `SkSweepGradient`
 * (`0xFF555555 -> 0xFF444444`) centred at (400, 400). Dither-on twin of
 * `ShallowGradientSweepNoDitherGM` -- the GM body is identical, only the
 * upstream reference differs. Our rasterizer never applies dither, so
 * the GPU output is identical to the no-dither path ; this test exposes
 * the dither-induced drift between our undithered output and the
 * dithered reference (which stays sub-LSB on this near-grey ramp).
 *
 * Both backends land effectively byte-exact (raster 99.99 % / GPU
 * 100.00 %). Floors :
 *  - raster (`Round9Test`, tol=1) : 99.95 % ;
 *  - GPU (`ShallowGradientSweepWebGpuTest`, tol=8) : 99.95 %.
 */
class ShallowGradientSweepDitherCrossBackendTest {

    @Test
    fun `ShallowGradientSweepDitherGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientSweepDitherGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
