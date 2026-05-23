package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps not implemented")
class WackyYUVFormatsTest {

    @Test
    fun `WackyYUVFormatsGM placeholder`() {
        TestUtils.runGmTest(WackyYUVFormatsGM())
    }
}
