package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CircularArcsFillGM

/**
 * Cross-test : `CircularArcsFillGM` on the GPU backend.
 *
 * Port of upstream `gm/circulararcs.cpp::circular_arcs_fill`. 4-quadrant
 * grid (`useCenter` ∈ {false, true}, AA ∈ {off, on}) ; each quadrant is
 * an 8×8 grid of `drawArc(rect, start, sweep, useCenter, p)` cells
 * (512 filled arcs in total, translucent alpha=100, with each cell
 * drawn twice — once red with `sweep`, once blue with `-(360 - sweep)`
 * so the overlap shows as magenta).
 *
 * Verifies `drawArc` filled routing across the full
 * (start, sweep, useCenter, AA) matrix on the GPU device. The CPU
 * floor sits at 65 % because of cumulative drift across the 512
 * translucent-overlap cells ; the GPU floor is sized to absorb the
 * same drift plus any AA edge convention differences.
 */
class CircularArcsFillWebGpuTest {

    @Test
    fun `CircularArcsFillGM renders close to reference PNG on the GPU backend`() {
        // 1000 × 1000 ; observed 98.91 % on Apple M2 Max. Notably
        // higher than the CPU floor of 65 % — the GPU AA gradient
        // coverage absorbs the translucent-overlap drift better.
        runGpuCrossTest(CircularArcsFillGM(), floor = 98.85)
    }
}
