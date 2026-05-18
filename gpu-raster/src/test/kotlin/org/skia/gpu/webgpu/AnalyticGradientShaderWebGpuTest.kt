package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnalyticGradientShaderGM

/**
 * Cross-test : `AnalyticGradientShaderGM` on the GPU backend.
 *
 * 8 x 4 grid of `kClamp` linear gradients with 1 to 8 interpolation
 * intervals each. Mixed smooth transitions + hardstops (duplicate
 * positions) across rows ; per-cell colour count ranges from 2 to 16
 * (which is exactly our `MAX_GRADIENT_STOPS` cap, so this also stress-
 * tests the upper-bound of the uniform stop table). Translates between
 * cells are axis-aligned, so each cell hits the G4.1
 * `path.isRect + axis-aligned-CTM` fast path.
 */
class AnalyticGradientShaderWebGpuTest {

    @Test
    fun `AnalyticGradientShaderGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(AnalyticGradientShaderGM(), floor = 99.95)
    }
}
