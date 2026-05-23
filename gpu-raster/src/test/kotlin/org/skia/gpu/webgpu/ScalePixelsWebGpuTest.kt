package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ScalePixelsGM

@Disabled("STUB.PIXMAP_SCALE: requires SkPixmap.scalePixels")
class ScalePixelsWebGpuTest {
    @Test
    fun `ScalePixelsGM placeholder`() {
        runGpuCrossTest(ScalePixelsGM(), floor = 0.0)
    }
}
