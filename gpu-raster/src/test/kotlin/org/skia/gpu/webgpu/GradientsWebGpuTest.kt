package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.GradientsGM

/**
 * G-suivi (round 13) cross-test : `GradientsGM` -- 840 x 815 grid of
 * 6 columns x 5 rows of 100 x 100 drawRects. Rows iterate over five
 * gradient types : linear / radial / sweep / 2-conical focal-inside /
 * 2-conical focal-outside. Columns iterate over six colour-stop
 * configurations (2 / 3 / 5-stop + hardstop). All kClamp tile mode,
 * axis-aligned CTM, no rotation.
 *
 * Five of the six rows are fully in-scope on GPU (linear, radial,
 * sweep, 2-conical focal-inside). The last row (focal-outside) falls
 * through to the solid-color machinery -- that depresses the floor.
 */
class GradientsWebGpuTest {

    @Test
    fun `GradientsGM renders close to reference PNG on the GPU backend`() {
        // Landing score 90.12%. The last row (2-conical focal-
        // outside) still falls through to the solid-color machinery,
        // taking ~10% of pixels off the textual band. The four
        // in-scope rows match closely.
        runGpuCrossTest(GradientsGM(), floor = 90.05)
    }
}
