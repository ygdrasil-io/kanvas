package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (librsvg) emoji table dispatch via JNI — see API_FINALIZATION_PLAN.md")
class ScaledemojiRenderingTest {

    @Test
    fun `ScaledemojiRenderingGM matches reference`() {
        val gm = ScaledemojiRenderingGM()
        TestUtils.runGmTest(gm)
    }
}
