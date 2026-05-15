package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (librsvg) emoji table dispatch via JNI — see API_FINALIZATION_PLAN.md")
class ColoremojiBlendmodesTest {

    @Test
    fun `ColoremojiBlendmodesGM matches reference`() {
        val gm = ColoremojiBlendmodesGM()
        TestUtils.runGmTest(gm)
    }
}
