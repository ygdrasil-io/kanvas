package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (librsvg) emoji table dispatch via JNI — see API_FINALIZATION_PLAN.md")
class ScaledemojiTest {

    @Test
    fun `ScaledemojiGM matches reference`() {
        val gm = ScaledemojiGM()
        TestUtils.runGmTest(gm)
    }
}
