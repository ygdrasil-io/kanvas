package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.GradientDirtyLaundryGM

/**
 * Cross-backend test : `GradientDirtyLaundryGM` on raster + GPU.
 *
 * 640 x 615 canvas (BG 0xFFDDDDDD) with three 100 x 100 drawRects
 * stacked vertically at (20, 20), each filled with a 40-stop kClamp
 * gradient (linear / radial / sweep). The 40 entries replay the
 * 5-colour `{R, G, B, W, K}` pattern eight times -- a regression
 * marker for banding in the gradient sampler's per-stop lerp
 * arithmetic. In scope on the GPU : linear (G4.0), radial (G4.1),
 * sweep (G4.3), all on rect with kClamp and axis-aligned CTM.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`GradientDirtyLaundryTest`, tol=1) : 93.1 % ;
 *  - GPU (`GradientDirtyLaundryWebGpuTest`, tol=8) : 94.40 % (drift
 *    concentrated on the dense 40-stop sampler's per-stop lerp
 *    boundaries -- a few LSB of channel jitter per row).
 */
class GradientDirtyLaundryCrossBackendTest {

    @Test
    fun `GradientDirtyLaundryGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = GradientDirtyLaundryGM(),
            rasterFloor = 93.1,
            gpuFloor = 94.40,
            rasterTolerance = 1,
        )
    }
}
