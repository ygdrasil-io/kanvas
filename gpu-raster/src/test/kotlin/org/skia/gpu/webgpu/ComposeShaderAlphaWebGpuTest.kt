package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ComposeShaderAlphaGM

@Disabled("STUB.COMPOSE_SHADER: needs SkShaders.Blend(mode, dst, src) compose-shader factory")
class ComposeShaderAlphaWebGpuTest {
    @Test
    fun `ComposeShaderAlphaGM placeholder`() {
        runGpuCrossTest(ComposeShaderAlphaGM(), floor = 0.0)
    }
}
