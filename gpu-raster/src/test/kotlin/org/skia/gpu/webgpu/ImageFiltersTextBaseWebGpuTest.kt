package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ImageFiltersTextIfGM

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs")
class ImageFiltersTextBaseWebGpuTest {
    @Test
    fun `ImageFiltersTextIfGM placeholder`() {
        runGpuCrossTest(ImageFiltersTextIfGM(), floor = 0.0)
    }
}
