package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.FillTypesGM

/**
 * G-suivi (round 18) cross-test : `FillTypesGM` -- port of upstream
 * `gm/filltypes.cpp::FillTypeGM`.
 *
 * 835 x 840, grey background, 4 quadrants -- 2 x 2 (scale {1, 5/4} x
 * AA {off, on}) -- each split into a 2 x 2 sub-grid of fill rules
 * (kWinding / kEvenOdd / kInverseWinding / kInverseEvenOdd). Each cell
 * clips to a 150 x 150 rect, fills the cell white, then translates +
 * scales + draws a 2-circle path (concentric overlap regression).
 *
 * Exercises the full G3.3b.3b fill-rule matrix on multi-contour paths
 * under both AA and non-AA, with the additional drawColor + clipRect
 * background fill per cell. Mirror of the upstream regression for
 * inverse-fill correctness with multi-contour input.
 *
 * Reference : `filltypes.png` (note the trailing `s` in upstream's
 * `filltypes` reference, even though the kotlin port class name is
 * `FillTypesGM` and `gm.name()` returns `"filltypes"`).
 */
class FillTypesWebGpuTest {

    @Test
    fun `FillTypesGM renders close to reference PNG on the GPU backend`() {
        // Landing score 99.55 %. Floor set 0.05 % below for scoring
        // drift headroom.
        runGpuCrossTest(FillTypesGM(), floor = 99.50)
    }
}
