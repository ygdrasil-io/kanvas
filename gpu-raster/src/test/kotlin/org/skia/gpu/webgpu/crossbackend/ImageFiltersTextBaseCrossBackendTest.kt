package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ImageFiltersTextIfGM

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs")
class ImageFiltersTextBaseCrossBackendTest {
    @Test
    fun `ImageFiltersTextIfGM placeholder`() {
        runCrossBackendTest(ImageFiltersTextIfGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
