package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.PATH_TORTURE_ASSET: requires bundled bad-apple torture-test path data")
class BadAppleTest {

    @Test
    fun `BadAppleGM matches reference`() {
        val gm = BadAppleGM()
        TestUtils.runGmTest(gm)
    }
}
