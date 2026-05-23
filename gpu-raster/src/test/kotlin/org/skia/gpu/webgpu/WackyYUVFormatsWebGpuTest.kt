package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.WackyYUVFormatsGM

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps not implemented")
class WackyYUVFormatsWebGpuTest {
    @Test
    fun `WackyYUVFormatsGM placeholder`() {
        runGpuCrossTest(WackyYUVFormatsGM(), floor = 0.0)
    }
}
