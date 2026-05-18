package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircularArcsStrokeRoundGM

/**
 * Cross-test : `CircularArcsStrokeRoundGM` on the GPU backend.
 *
 * Port of upstream `gm/circulararcs.cpp::circular_arcs_stroke_round`.
 * Same 4-quadrant × 8×8 cell layout as `CircularArcsFillGM`, but with
 * `paint.style = kStroke_Style`, `strokeWidth = 15`, and
 * `paint.strokeCap = kRound_Cap`.
 *
 * Verifies `drawArc` stroked routing with round caps on the GPU.
 */
class CircularArcsStrokeRoundWebGpuTest {

    @Test
    fun `CircularArcsStrokeRoundGM renders close to reference PNG on the GPU backend`() {
        // 1000 × 1000 ; observed 95.81 % on Apple M2 Max. Round caps
        // amplify per-pixel drift on each of the 512 stroked arcs.
        runGpuCrossTest(CircularArcsStrokeRoundGM(), floor = 95.75)
    }
}
