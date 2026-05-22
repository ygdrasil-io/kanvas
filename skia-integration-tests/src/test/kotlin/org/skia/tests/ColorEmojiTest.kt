package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (lib)rsvg color-emoji table dispatch via JNI — see API_FINALIZATION_PLAN.md")
class ColorEmojiTest {

    @Test
    fun `ColorEmojiGM matches reference`() {
        val gm = ColorEmojiGM()
        TestUtils.runGmTest(gm)
    }
}
