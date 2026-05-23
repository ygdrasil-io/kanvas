package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps + GPU texture YUV path not implemented (GPU-only GM)")
class ImageFromYUVTexturesTest {

    @Test
    fun `ImageFromYUVTexturesGM matches reference`() {
        val gm = ImageFromYUVTexturesGM()
        TestUtils.runGmTest(gm)
    }
}
