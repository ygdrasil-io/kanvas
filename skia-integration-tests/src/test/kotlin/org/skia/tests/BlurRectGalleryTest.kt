package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.BLURRECT_GALLERY: needs SkBlurMask.BlurRect (analytic rect blur kernel) in :kanvas-skia")
class BlurRectGalleryTest {

    @Test
    fun `BlurRectGalleryGM matches reference`() {
        val gm = BlurRectGalleryGM()
        TestUtils.runGmTest(gm)
    }
}
