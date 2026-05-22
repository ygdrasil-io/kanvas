package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.FillrectGradientGM

/**
 * Cross-backend test : `FillrectGradientGM` on raster + GPU.
 *
 * 120 x 540 canvas, 2-column x 9-row grid of 50 x 50 rect cells. Each
 * row is the same stop list rendered once with `SkLinearGradient`
 * (column 0) and once with `SkRadialGradient` (column 1). Stops cover
 * the gradient infrastructure corner cases : 2/3-stop endpoints, sub-
 * range, single-stop, disjoint via duplicate position, ignored
 * duplicates, unsorted input. All under kClamp on rect (in scope).
 *
 * The raster floor is low (60 %) because raster does 8-bit lerp vs
 * upstream F16 and drifts hard on the row-9 unsorted-stop case.
 * GPU lands much closer to upstream (~96 %).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`FillrectGradientTest`, tol=1) : 60.0 % ;
 *  - GPU (`FillrectGradientWebGpuTest`, tol=8) : 95.70 %.
 */
class FillrectGradientCrossBackendTest {

    @Test
    fun `FillrectGradientGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = FillrectGradientGM(),
            rasterFloor = 60.0,
            gpuFloor = 95.70,
            rasterTolerance = 1,
        )
    }
}
