package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnisotropicGM

@Disabled("ALIAS: already ported as AnisotropicImageScale{Linear,Mip,Aniso}WebGpuTest")
class AnisotropicWebGpuTest {
    @Test
    fun `AnisotropicGM placeholder alias`() {
        runGpuCrossTest(AnisotropicGM(), floor = 0.0)
    }
}
