package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ClampedGradientsGM

/**
 * G4.x cross-test : `ClampedGradientsGM` -- 640 x 510 canvas with
 * a 0xFFDDDDDD background (via drawPaint) and a single drawRect
 * (100 x 300, translated by (20, 20)) filled with a 5-stop kClamp
 * `SkRadialGradient` (red/green/blue/white/black) centred at (0, 300)
 * with radius 200 -- centre is outside the drawn rect so every pixel
 * sees a non-trivial radial distance.
 *
 * Pure radial-on-rect + drawPaint workout, all in-scope on GPU.
 */
class ClampedGradientsWebGpuTest {

    @Test
    fun `ClampedGradientsGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ClampedGradientsGM(), floor = 99.95)
    }
}
