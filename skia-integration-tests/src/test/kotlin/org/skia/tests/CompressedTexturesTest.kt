package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/ETC1/ASTC compressed texture decode + GPU upload")
class CompressedTexturesTest {

    @Test
    fun `CompressedTexturesGM matches reference`() {
        val gm = CompressedTexturesGM()
        TestUtils.runGmTest(gm)
    }
}
