package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class MakeColorSpaceTest {

    @Test
    fun `MakeColorSpaceGM matches makecolorspace reference`() {
        val gm = MakeColorSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
