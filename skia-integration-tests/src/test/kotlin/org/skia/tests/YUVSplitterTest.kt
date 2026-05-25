package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GPU_YUVA_TEXTURE_PATH: YUV splitter CPU planes are implemented; raster GM sink still blocks texture-style assembly parity")
class YUVSplitterTest {

    @Test
    fun `YUVSplitterGM placeholder`() {
        TestUtils.runGmTest(YUVSplitterGM())
    }
}
