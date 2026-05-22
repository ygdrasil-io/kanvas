package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BadAppleGM

@Disabled("STUB.PATH_TORTURE_ASSET: requires bundled bad-apple torture-test path data")
class BadAppleWebGpuTest {
    @Test
    fun `BadAppleGM placeholder`() {
        runGpuCrossTest(BadAppleGM(), floor = 0.0)
    }
}
