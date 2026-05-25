package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class ColorEmojiTest {

    @Test
    fun `ColorEmojiGM matches reference`() {
        val gm = ColorEmojiGM()
        TestUtils.runGmTest(gm)
    }
}
