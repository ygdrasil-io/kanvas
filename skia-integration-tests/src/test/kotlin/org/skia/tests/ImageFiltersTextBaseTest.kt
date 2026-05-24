package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class ImageFiltersTextBaseTest {

    @Test
    fun `ImageFiltersTextIfGM renders text image filter smoke`() {
        TestUtils.runGmTest(ImageFiltersTextIfGM())
    }

    @Test
    fun `ImageFiltersTextCfGM renders text color filter smoke`() {
        TestUtils.runGmTest(ImageFiltersTextCfGM())
    }
}
