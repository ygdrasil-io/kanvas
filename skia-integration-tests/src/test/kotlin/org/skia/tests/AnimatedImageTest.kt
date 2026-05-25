package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class AnimatedImageTest {

    @Test
    fun `AnimatedImageGM matches reference`() {
        val gm = AnimatedImageGM()
        TestUtils.runGmTest(gm)
    }
}
