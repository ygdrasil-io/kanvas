package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircularArcsStrokeSquareGM

/**
 * Cross-test : `CircularArcsStrokeSquareGM` on the GPU backend.
 *
 * Port of upstream `gm/circulararcs.cpp::circular_arcs_stroke_square`.
 * Same 4-quadrant × 8×8 cell layout as `CircularArcsFillGM`, but with
 * `paint.style = kStroke_Style`, `strokeWidth = 15`, and
 * `paint.strokeCap = kSquare_Cap`.
 *
 * Verifies `drawArc` stroked routing with square caps on the GPU.
 */
class CircularArcsStrokeSquareWebGpuTest {

    @Test
    fun `CircularArcsStrokeSquareGM renders close to reference PNG on the GPU backend`() {
        // 1000 × 1000 ; observed 96.68 % on Apple M2 Max.
        runGpuCrossTest(CircularArcsStrokeSquareGM(), floor = 96.6)
    }
}
