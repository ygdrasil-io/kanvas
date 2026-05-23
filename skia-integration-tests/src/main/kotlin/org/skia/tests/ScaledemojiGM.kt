package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.graphiks.math.SkISize

/**
 * STUB.EMOJI_TABLES consumer GM. Iso-aligned port of upstream's
 * `gm/scaledemoji.cpp::ScaledEmojiGM` (registered as
 * `scaledemoji_<font-format>`, 1200 x 1200), which renders a
 * colour-emoji text string via `SkCanvas::drawSimpleText` at four
 * progressively larger point sizes (70 / 180 / 270 / 340) to verify
 * that the bitmap-scaler and SDF / path dispatch paths all agree
 * across colour-emoji typefaces.
 *
 * The body touches [EmojiTypeface.create] so the compile
 * contract holds. [ScaledemojiTest] is `@Disabled` because the
 * dispatch throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ScaledemojiGM : GM() {

    override fun getName(): String = "scaledemoji"
    override fun getISize(): SkISize = SkISize.Make(1200, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.CBDT, SkData.MakeWithCopy(ByteArray(0)))
    }
}
