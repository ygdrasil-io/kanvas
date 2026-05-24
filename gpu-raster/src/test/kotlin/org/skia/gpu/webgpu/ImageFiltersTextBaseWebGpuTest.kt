package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ImageFiltersTextIfGM

@Disabled("WebGPU text filter dependency: raster textfilter GMs are covered by ImageFiltersTextTest")
class ImageFiltersTextBaseWebGpuTest {
    @Test
    fun `ImageFiltersTextIfGM placeholder`() {
        runGpuCrossTest(ImageFiltersTextIfGM(), floor = 0.0)
    }
}
