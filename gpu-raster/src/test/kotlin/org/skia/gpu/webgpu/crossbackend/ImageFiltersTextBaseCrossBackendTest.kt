package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ImageFiltersTextIfGM

@Disabled("WebGPU text filter dependency: raster textfilter GMs are covered by ImageFiltersTextTest")
class ImageFiltersTextBaseCrossBackendTest {
    @Test
    fun `ImageFiltersTextIfGM placeholder`() {
        runCrossBackendTest(ImageFiltersTextIfGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
