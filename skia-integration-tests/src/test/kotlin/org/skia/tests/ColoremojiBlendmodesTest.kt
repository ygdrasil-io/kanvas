package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.foundation.emoji.EmojiTypeface
import org.skia.testing.TestUtils

/**
 * Tests for [ColoremojiBlendmodesGM] — one per upstream format variant.
 *
 * All five tests are `@Disabled` because [EmojiTypeface.create] throws
 * `STUB.EMOJI_TABLES` at runtime — colour-emoji table dispatch requires
 * FreeType (and librsvg for SVG) via JNI.
 *
 * Upstream GMs:
 *  - `coloremoji_blendmodes_colrv0`
 *  - `coloremoji_blendmodes_sbix`
 *  - `coloremoji_blendmodes_cbdt`
 *  - `coloremoji_blendmodes_test`
 *  - `coloremoji_blendmodes_svg`
 */
@Disabled("STUB.EMOJI_TABLES: requires FreeType + (librsvg) emoji table dispatch via JNI — see API_FINALIZATION_PLAN.md")
class ColoremojiBlendmodesTest {

    @Test
    fun `coloremoji_blendmodes_colrv0 matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.COLRv0))
    }

    @Test
    fun `coloremoji_blendmodes_sbix matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.Sbix))
    }

    @Test
    fun `coloremoji_blendmodes_cbdt matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.CBDT))
    }

    @Test
    fun `coloremoji_blendmodes_svg matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.SVG))
    }
}
