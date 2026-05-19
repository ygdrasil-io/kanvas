package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PointsMaskFilterGM

/**
 * Cross-backend test : `PointsMaskFilterGM` (`points_maskfilter`)
 * on raster + GPU.
 *
 * 512 x 256 two-column layout. For each of `(kSquare_Cap,
 * kRound_Cap)` the GM draws 30 random points (seed-stable
 * SkRandom) via `drawPoints(kPoints_PointMode, ..., paint)` twice :
 *  - first as fat black discs with `paint.maskFilter =
 *    SkBlurMaskFilter(kNormal, sigma = 6)` + strokeWidth = 10,
 *  - then as red unblurred discs on top.
 *
 * Exercises `drawPoints(kPoints)` (stroker emits one disc per
 * point, kSquare = rect, kRound = circle) composed with the
 * #570 / #575 `SkBlurMaskFilter(kNormal)` unlock. Sigma 6 is
 * large enough that the kSquare halo bleeds across point
 * boundaries -- non-trivial blend overlap stress for the mask
 * pipeline.
 *
 * Floors (observed) :
 *  - raster (tol = 8) : 96.60 %
 *  - GPU (tol = 8)    : 99.65 %
 * The 3 % GPU-over-raster lead is the F16 intermediate's better
 * precision on the additive blend of 30 overlapping halos.
 * Floors set 0.05 % below observed.
 */
class PointsMaskFilterCrossBackendTest {

    @Test
    fun `PointsMaskFilterGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PointsMaskFilterGM(),
            rasterFloor = 96.55,
            gpuFloor = 99.60,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
