package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnisotropicGM

@Disabled("ALIAS: already ported as AnisotropicImageScale{Linear,Mip,Aniso}CrossBackendTest")
class AnisotropicCrossBackendTest {
    @Test
    fun `AnisotropicGM placeholder alias`() {
        runCrossBackendTest(AnisotropicGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
