package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.RgbwSweepGradientGM

/**
 * G4.3 cross-test : `RgbwSweepGradientGM` -- 100 x 100 canvas with a
 * single drawRect filled with a 4-hardstop `SkSweepGradient` (kClamp)
 * centred at (50, 50). The four sectors (white / blue / red / green)
 * line up with the +X / +Y / -X / -Y axes ; pure sweep-on-rect, no
 * CTM transform.
 *
 * Pure sweep-gradient-on-rect workout, all in-scope on GPU.
 */
class RgbwSweepGradientWebGpuTest {

    @Test
    fun `RgbwSweepGradientGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(
            RgbwSweepGradientGM(),
            floor = 95.0,
            logTag = "RgbwSweepGradientWebGpu",
        )
    }
}
