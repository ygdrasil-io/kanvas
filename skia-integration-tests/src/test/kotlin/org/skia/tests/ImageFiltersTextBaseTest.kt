package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs")
class ImageFiltersTextBaseTest {

    @Test
    fun `ImageFiltersTextBaseGM placeholder`() {
        TestUtils.runGmTest(ImageFiltersTextIfGM())
    }
}
