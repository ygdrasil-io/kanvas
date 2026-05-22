package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CompositorGM

@Disabled("ALIAS: partial coverage in CompositorQuadsImageGM; full matrix TODO")
class CompositorWebGpuTest {
    @Test
    fun `CompositorGM placeholder`() {
        runGpuCrossTest(CompositorGM(), floor = 0.0)
    }
}
