package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COMPRESSED_TEXTURES: requires ETC1/ETC2 and BC1/DXT1 block-compression decode + raster/GPU upload")
class ExoticFormatsTest {

    @Test
    fun `ExoticFormatsGM matches reference`() {
        val gm = ExoticFormatsGM()
        TestUtils.runGmTest(gm)
    }
}
