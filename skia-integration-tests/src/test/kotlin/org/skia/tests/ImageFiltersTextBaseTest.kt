package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.TEXT_IMAGE_FILTER: requires SkPaint.imageFilter for text glyph runs — ImageFiltersTextBaseGM is abstract, concrete subclasses are tested via ImageFiltersTextGM")
class ImageFiltersTextBaseTest {

    @Test
    fun `ImageFiltersTextBaseGM placeholder`() {
        // ImageFiltersTextBaseGM is abstract — cannot be instantiated directly.
        // Concrete subclasses (e.g. ImageFiltersTextGM) exercise the actual drawing.
    }
}
