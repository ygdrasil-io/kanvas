package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps + makeColorSpace not implemented")
class YUVMakeColorSpaceTest {

    @Test
    fun `YUVMakeColorSpaceGM placeholder`() {
        TestUtils.runGmTest(YUVMakeColorSpaceGM())
    }
}
