package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ScalePixelsGM

@Disabled("STUB.PIXMAP_SCALE: requires SkPixmap.scalePixels")
class ScalePixelsCrossBackendTest {
    @Test
    fun `ScalePixelsGM placeholder`() {
        runCrossBackendTest(ScalePixelsGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
