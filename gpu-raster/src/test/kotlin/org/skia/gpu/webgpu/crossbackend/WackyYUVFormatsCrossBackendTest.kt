package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.WackyYUVFormatsGM

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps not implemented")
class WackyYUVFormatsCrossBackendTest {
    @Test
    fun `WackyYUVFormatsGM placeholder`() {
        runCrossBackendTest(WackyYUVFormatsGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
