package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ScaledTilingGradientGM

/**
 * O6 cross-test : `ScaledTilingGradientGM` (`scaled_tilemode_gradient`,
 * 650x610) on the GPU backend. 3x3 grid of gradient shaders under
 * varying (tx, ty) tile-mode combos. Text labels skipped — see
 * [ScaledTiling2GM] Javadoc.
 */
class ScaledTilingGradientWebGpuTest {
    @Test
    fun `ScaledTilingGradientGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ScaledTilingGradientGM(), floor = 50.0)
    }
}
