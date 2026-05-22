package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CircularArcsHairlineGM

/**
 * Cross-backend test : `CircularArcsHairlineGM` on raster + GPU.
 *
 * 1000 x 1000 ; same 4-quadrant x 8 x 8 cell layout as the other
 * `CircularArcs*GM` family, but with `paint.style = kStroke_Style` and
 * `strokeWidth = 0` (hairline). 512 hairline-stroked arcs across the
 * (start, sweep, useCenter, AA) matrix. Exercises `drawArc` hairline
 * routing (G3.4.3) on both backends. The hairline / AA pixel coverage
 * diverges much less than the translucent fill variant, so cross-validation
 * is meaningful here (the Fill / StrokeButt / StrokeRound / StrokeSquare
 * siblings stay raster-only -- raster floors at ~45-67 % skip the >=90 %
 * cross-backend threshold).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round6Test`, tol=1) : 92.0 % ;
 *  - GPU (`CircularArcsHairlineWebGpuTest`, tol=8) : 97.05 %.
 */
class CircularArcsHairlineCrossBackendTest {

    @Test
    fun `CircularArcsHairlineGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = CircularArcsHairlineGM(),
            rasterFloor = 92.0,
            gpuFloor = 97.05,
            rasterTolerance = 1,
        )
    }
}
