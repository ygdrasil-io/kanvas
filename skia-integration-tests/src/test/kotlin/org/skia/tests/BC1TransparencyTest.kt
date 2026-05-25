package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class BC1TransparencyTest {

    @Test
    fun `BC1TransparencyGM matches reference`() {
        val gm = BC1TransparencyGM()
        TestUtils.runGmTest(gm)
    }
}
