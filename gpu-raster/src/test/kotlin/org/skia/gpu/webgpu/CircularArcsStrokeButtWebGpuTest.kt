package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircularArcsStrokeButtGM

/**
 * Cross-test : `CircularArcsStrokeButtGM` on the GPU backend.
 *
 * Port of upstream `gm/circulararcs.cpp::circular_arcs_stroke_butt`.
 * Same 4-quadrant × 8×8 cell layout as `CircularArcsFillGM`, but with
 * `paint.style = kStroke_Style`, `strokeWidth = 15`, and
 * `paint.strokeCap = kButt_Cap`. 512 stroked-butt arcs across the
 * (start, sweep, useCenter, AA) matrix.
 *
 * Verifies `drawArc` stroked routing with butt caps on the GPU.
 */
class CircularArcsStrokeButtWebGpuTest {

    @Test
    fun `CircularArcsStrokeButtGM renders close to reference PNG on the GPU backend`() {
        // 1000 × 1000 ; observed 96.87 % on Apple M2 Max.
        runGpuCrossTest(CircularArcsStrokeButtGM(), floor = 96.8)
    }
}
