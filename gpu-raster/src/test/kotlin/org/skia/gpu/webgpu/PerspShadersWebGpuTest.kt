package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PerspShadersGM

/**
 * Perspective shader scenes keep a low but non-zero floor because the
 * imported GM documents partial rendering while perspective image sampling
 * is still a known gap.
 */
class PerspShadersWebGpuTest {
    @Test
    fun `PerspShadersGM aa renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(PerspShadersGM(), floor = 20.0)
    }

    @Test
    fun `PerspShadersGM bw renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(PerspShadersGM.bw(), floor = 20.0)
    }
}
