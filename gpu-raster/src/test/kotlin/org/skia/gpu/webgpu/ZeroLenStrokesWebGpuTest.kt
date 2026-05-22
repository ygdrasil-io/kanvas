package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ZeroLenStrokesGM

/**
 * O6 cross-test : `ZeroLenStrokesGM` (`zeroPath`, 400x800) on the GPU
 * backend. Stresses zero-length / degenerate path stroking with
 * round + square caps (crbug.com/422974 regression).
 */
class ZeroLenStrokesWebGpuTest {
    @Test
    fun `ZeroLenStrokesGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ZeroLenStrokesGM(), floor = 50.0)
    }
}
