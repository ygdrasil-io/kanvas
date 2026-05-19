package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurCirclesGM

/**
 * Cross-backend test : `BlurCirclesGM` on raster + GPU.
 *
 * 4 x 4 grid of drawCircle calls under varying blur radii
 * (1, 5, 10, 20 px) and circle radii (5, 10, 25, 50 px), each rotated
 * by `j * 22 deg` about its centre. Exercises
 * `SkBlurMaskFilter(kNormal)` (the #570 unlock) on a rotated CTM with
 * a solid black fill -- single-shape mask filter path stressing the
 * mask-bbox computation under non-trivial CTM.
 *
 * Floors :
 *  - raster (tol=8) : 91.92 %
 *  - GPU (tol=8)    : 89.79 %
 * Both inside the WARNING_BAND_PERCENT envelope (~2.1 pt gap). The
 * raster - GPU gap is the F16 intermediate's residual blend-precision
 * drift accumulated through the blur kernel's downstream blits.
 */
class BlurCirclesCrossBackendTest {

    @Test
    fun `BlurCirclesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BlurCirclesGM(),
            rasterFloor = 91.87,
            gpuFloor = 89.74,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
