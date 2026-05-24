package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ImageFiltersTextBaseGM

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs")
class ImageFiltersTextBaseWebGpuTest {
    @Test
    fun `ImageFiltersTextBaseGM placeholder`() {
        runGpuCrossTest(ImageFiltersTextBaseGM(), floor = 0.0)
    }
}
