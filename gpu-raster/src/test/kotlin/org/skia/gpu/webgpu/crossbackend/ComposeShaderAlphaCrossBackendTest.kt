package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ComposeShaderAlphaGM

@Disabled("STUB.COMPOSE_SHADER: needs SkShaders.Blend(mode, dst, src) compose-shader factory")
class ComposeShaderAlphaCrossBackendTest {
    @Test
    fun `ComposeShaderAlphaGM placeholder`() {
        runCrossBackendTest(ComposeShaderAlphaGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
