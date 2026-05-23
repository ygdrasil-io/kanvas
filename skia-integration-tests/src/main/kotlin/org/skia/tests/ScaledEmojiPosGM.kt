package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.graphiks.math.SkISize

/**
 * STUB.EMOJI_TABLES consumer GM. Iso-aligned port of upstream's
 * `gm/scaledemoji_pos.cpp::ScaledEmojiPosGM` (registered as
 * `scaledemojipos_<font-format>`, 950 x 950), which composites
 * colour-emoji glyphs at per-glyph positions via
 * `SkCanvas.drawGlyphs(positions=...)` to verify that the
 * per-glyph-pos path agrees with the auto-advance path for
 * bitmap-emoji typefaces.
 *
 * The body touches [EmojiTypeface.create] so the compile
 * contract holds. The matching test is `@Disabled` because the
 * dispatch throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * STUB.EMOJI_TABLES.
 */
public class ScaledEmojiPosGM : GM() {

    override fun getName(): String = "scaledemojipos"
    override fun getISize(): SkISize = SkISize.Make(950, 950)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch -- throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.CBDT, SkData.MakeWithCopy(ByteArray(0)))
    }
}
