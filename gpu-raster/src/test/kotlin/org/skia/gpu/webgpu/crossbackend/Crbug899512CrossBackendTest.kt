package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug899512GM

/**
 * Cross-backend test : `Crbug899512GM` (`crbug_899512`) on raster + GPU.
 *
 * 520 x 520 GM. Concats a flipped CTM `[-1 0 220 ; 0 1 0 ; 0 0 1]`
 * (X-mirror around x = 110, origin shift to 220), then draws a
 * 200 x 200 rect with `paint.maskFilter = SkBlurMaskFilter(kNormal,
 * sigma = 6.2735)` + `paint.colorFilter = SkColorFilters.Blend(BLACK,
 * kSrcIn)`. Repro for the flipped-CTM blur-clipping bug : pre-fix the
 * blur margin used `det(ctm)` (negative on X-mirror) instead of
 * `|det(ctm)|`, so pixels on the right edge were dropped.
 *
 * Exercises the #569 + #570 + #575 stack : paint.colorFilter on
 * direct draws (Blend SrcIn solid-color route) composed with
 * SkBlurMaskFilter(kNormal) under a non-axis-aligned (flipped) CTM
 * the device's `isAxisAligned` predicate is X-reflection-tolerant.
 *
 * Floors (observed) :
 *  - raster (tol = 8) : 95.50 %
 *  - GPU (tol = 8)    : 100.00 %
 * The 4.5 % GPU-over-raster lead is the colorFilter routing : on
 * GPU the colorFilter is applied as a paint-side step (uniform
 * Blend) and lands byte-exact against the reference, while raster
 * picks up sub-pixel drift through its mask-blur intermediate.
 * Floors set 0.05 % below observed (raster) / pin at 99.95 % (GPU).
 */
class Crbug899512CrossBackendTest {

    @Test
    fun `Crbug899512GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug899512GM(),
            rasterFloor = 95.45,
            gpuFloor = 99.95,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
