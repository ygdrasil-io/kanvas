package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class MakeColorTypeAndSpaceTest {

    @Test
    fun `MakeColorTypeAndSpaceGM matches makecolortypeandspace reference`() {
        val gm = MakeColorTypeAndSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
