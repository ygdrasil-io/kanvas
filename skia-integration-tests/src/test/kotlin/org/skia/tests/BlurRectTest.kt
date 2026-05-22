package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.BLUR_RECTS_FULL: needs make_radial + donut helpers + full SkBlurStyle x shader x clip x scale matrix")
class BlurRectTest {

    @Test
    fun `BlurRectGM matches reference`() {
        val gm = BlurRectGM()
        TestUtils.runGmTest(gm)
    }
}
