package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EMOJI_TABLES: colour-emoji typeface dispatch is stubbed")
class ScaledEmojiPosTest {

    @Test
    fun `ScaledEmojiPosGM placeholder`() {
        TestUtils.runGmTest(ScaledEmojiPosGM())
    }
}
