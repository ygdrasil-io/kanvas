package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnisotropicImageScaleAnisoGM

class AnisotropicImageScaleAnisoWebGpuTest {
    @Test
    fun `AnisotropicImageScaleAnisoGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(AnisotropicImageScaleAnisoGM(), floor = 25.0)
    }
}
