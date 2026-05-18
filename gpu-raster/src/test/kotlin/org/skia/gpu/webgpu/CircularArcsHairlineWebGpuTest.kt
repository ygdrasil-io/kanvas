package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircularArcsHairlineGM

/**
 * Cross-test : `CircularArcsHairlineGM` on the GPU backend.
 *
 * Same 4-quadrant × 8×8 cell layout as `CircularArcsFillGM`, but with
 * `paint.style = kStroke_Style` and `strokeWidth = 0` (hairline). 512
 * hairline-stroked arcs across the (start, sweep, useCenter, AA)
 * matrix.
 *
 * Verifies `drawArc` hairline routing on the GPU. CPU floor is 92 %
 * (the hairline / AA pixel coverage diverges much less than the
 * translucent fills) ; GPU floor sized at 50 initially.
 */
class CircularArcsHairlineWebGpuTest {

    @Test
    fun `CircularArcsHairlineGM renders close to reference PNG on the GPU backend`() {
        // 1000 × 1000 ; observed 97.13 % on Apple M2 Max.
        runGpuCrossTest(CircularArcsHairlineGM(), floor = 97.05)
    }
}
