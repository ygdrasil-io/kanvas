package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/DXT1 compressed texture decode + GPU upload")
class BC1TransparencyTest {

    @Test
    fun `BC1TransparencyGM matches reference`() {
        val gm = BC1TransparencyGM()
        TestUtils.runGmTest(gm)
    }
}
