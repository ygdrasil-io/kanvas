package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CompositorGM

@Disabled("ALIAS: partial coverage in CompositorQuadsImageGM; full matrix TODO")
class CompositorCrossBackendTest {
    @Test
    fun `CompositorGM placeholder`() {
        runCrossBackendTest(CompositorGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
