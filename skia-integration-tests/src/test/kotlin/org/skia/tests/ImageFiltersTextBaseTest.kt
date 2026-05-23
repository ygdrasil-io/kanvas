package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs — ImageFiltersTextBaseGM is abstract, concrete subclasses are tested via ImageFiltersTextGM")
class ImageFiltersTextBaseTest {

    @Test
    fun `ImageFiltersTextIfGM placeholder`() {
        TestUtils.runGmTest(ImageFiltersTextIfGM())
    }

    @Test
    fun `ImageFiltersTextCfGM placeholder`() {
        TestUtils.runGmTest(ImageFiltersTextCfGM())
    }
}
