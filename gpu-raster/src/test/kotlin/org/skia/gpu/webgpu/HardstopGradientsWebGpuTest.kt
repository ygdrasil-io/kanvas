package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.HardstopGradientShaderGM

/**
 * G4.x cross-test : `HardstopGradientShaderGM` -- 8 x 3 grid of
 * linear gradients on rect. Columns iterate through SkTileMode
 * (kClamp / kRepeat / kMirror) ; rows iterate through 8 stop
 * configurations (no positions / evenly spaced / hard stops at
 * various positions, 2..5 stops). First multi-tile-mode GM
 * cross-test exercising kRepeat and kMirror on real geometry.
 */
class HardstopGradientsWebGpuTest {

    @Test
    fun `HardstopGradientShaderGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(
            HardstopGradientShaderGM(),
            floor = 99.95,
            logTag = "HardstopGradientsWebGpu",
        )
    }
}
