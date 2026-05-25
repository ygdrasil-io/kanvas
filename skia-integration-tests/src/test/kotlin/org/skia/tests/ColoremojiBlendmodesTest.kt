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
class ColoremojiBlendmodesTest {

    @Test
    fun `coloremoji_blendmodes_colrv0 matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.COLRv0))
    }

    @Disabled("STUB.EMOJI_TABLES.SBIX_PNG_RENDER")
    @Test
    fun `coloremoji_blendmodes_sbix matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.Sbix))
    }

    @Disabled("STUB.EMOJI_TABLES.CBDT_PNG_RENDER")
    @Test
    fun `coloremoji_blendmodes_cbdt matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.CBDT))
    }

    @Disabled("STUB.EMOJI_TABLES.SVG")
    @Test
    fun `coloremoji_blendmodes_svg matches reference`() {
        TestUtils.runGmTest(ColoremojiBlendmodesGM(EmojiTypeface.Format.SVG))
    }
}
