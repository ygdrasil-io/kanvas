package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_WACKY_MATRIX_COVERAGE: GM requires broader matrix/layout parity than current CPU YUVA slice")
class WackyYUVFormatsTest {

    @Test
    fun `WackyYUVFormatsGM placeholder`() {
        TestUtils.runGmTest(WackyYUVFormatsGM())
    }
}
