package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class YUVMakeColorSpaceTest {

    @Test
    fun `YUVMakeColorSpaceGM matches reference`() {
        TestUtils.runGmTest(YUVMakeColorSpaceGM())
    }
}
