package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Tests for the three [AnimCodecPlayerExifGM] instances registered in
 * upstream `gm/animated_gif.cpp`:
 *
 *   DEF_GM(return new AnimCodecPlayerExifGM("images/required.webp");)
 *   DEF_GM(return new AnimCodecPlayerExifGM("images/required.gif");)
 *   DEF_GM(return new AnimCodecPlayerExifGM("images/stoplight_h.webp");)
 *
 * All three are MISSING_API — the [AnimCodecPlayerExifGM] body requires
 * `SkAnimCodecPlayer` + per-frame `Codec` seek, which are not yet
 * implemented in `:kanvas-skia`. The GM class is a flag-planting stub
 * that draws nothing; tests are disabled until the API lands.
 *
 * Upstream GM names (derived from `strrchr(fPath, '/') + 1`):
 *  - `AnimCodecPlayerExif_required.webp`
 *  - `AnimCodecPlayerExif_required.gif`
 *  - `AnimCodecPlayerExif_stoplight_h.webp`
 */
@Disabled("STUB.ANIM_CODEC_PLAYER: requires SkAnimCodecPlayer + Codec frame-by-frame seek")
class AnimCodecPlayerExifTest {

    /** Covers `DEF_GM(return new AnimCodecPlayerExifGM("images/required.webp");)` */
    @Test
    fun `AnimCodecPlayerExifGM required_webp matches reference`() {
        val gm = AnimCodecPlayerExifGM("images/required.webp")
        TestUtils.runGmTest(gm)
    }

    /** Covers `DEF_GM(return new AnimCodecPlayerExifGM("images/required.gif");)` */
    @Test
    fun `AnimCodecPlayerExifGM required_gif matches reference`() {
        val gm = AnimCodecPlayerExifGM("images/required.gif")
        TestUtils.runGmTest(gm)
    }

    /** Covers `DEF_GM(return new AnimCodecPlayerExifGM("images/stoplight_h.webp");)` */
    @Test
    fun `AnimCodecPlayerExifGM stoplight_h_webp matches reference`() {
        val gm = AnimCodecPlayerExifGM("images/stoplight_h.webp")
        TestUtils.runGmTest(gm)
    }
}
