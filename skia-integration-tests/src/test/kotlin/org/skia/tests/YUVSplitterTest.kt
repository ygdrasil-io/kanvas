package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_PIXMAPS: SkImages.TextureFromYUVAPixmaps not implemented (Ganesh/GPU-only GM)")
class YUVSplitterTest {

    @Test
    fun `YUVSplitterGM placeholder`() {
        TestUtils.runGmTest(YUVSplitterGM())
    }
}
