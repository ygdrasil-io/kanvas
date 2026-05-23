package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps + GPU image YUV path not implemented (GPU-only GM, Graphite only)")
class ImageFromYUVImagesTest {

    @Test
    fun `ImageFromYUVImagesGM matches reference`() {
        val gm = ImageFromYUVImagesGM()
        TestUtils.runGmTest(gm)
    }
}
