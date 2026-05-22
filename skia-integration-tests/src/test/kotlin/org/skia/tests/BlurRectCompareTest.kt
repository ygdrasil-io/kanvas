package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.BLUR_RECT_COMPARE: needs SkBlurMask.ComputeBlurredScanline brute-force reference vs analytic harness")
class BlurRectCompareTest {

    @Test
    fun `BlurRectCompareGM matches reference`() {
        val gm = BlurRectCompareGM()
        TestUtils.runGmTest(gm)
    }
}
