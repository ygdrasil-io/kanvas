package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class ReinterpretColorSpaceTest {

    @Test
    fun `ReinterpretColorSpaceGM matches reinterpretcolorspace reference`() {
        val gm = ReinterpretColorSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
