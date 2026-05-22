package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SimpleOffsetImageFilterGM

/**
 * O6 cross-backend : `SimpleOffsetImageFilterGM`
 * (`simple-offsetimagefilter`, 640x200) on raster + GPU.
 */
class SimpleOffsetImageFilterCrossBackendTest {
    @Test
    fun `SimpleOffsetImageFilterGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = SimpleOffsetImageFilterGM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
