package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SliverPathsGM

/**
 * O6 cross-test : `SliverPathsGM` (`mandoline`, 560x475) on the GPU
 * backend. AA path rasterization on sliver-cone contours produced by
 * recursive curve chopping at random `2^-k` t values.
 */
class SliverPathsWebGpuTest {
    @Test
    fun `SliverPathsGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SliverPathsGM(), floor = 50.0)
    }
}
