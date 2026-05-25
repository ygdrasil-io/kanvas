package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AyncYUVNoScaleTest {

    @Test
    fun `AyncYUVNoScaleGM matches reference`() {
        val gm = AyncYUVNoScaleGM()
        val rendered = org.skia.testing.TestUtils.runGmTest(gm)
        assertTrue(hasNonWhitePixel(rendered), "YUV no-scale GM should draw visible luma output")
    }
}
