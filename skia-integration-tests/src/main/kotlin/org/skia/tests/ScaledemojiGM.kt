package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.EMOJI_TABLES** consumer GM. Iso-aligned port of
 * upstream's `gm/scaledemoji.cpp` (which renders a colour-emoji
 * glyph at progressively larger sizes to spot bitmap-vs-vector
 * scaler dispatch issues).
 *
 * The body touches [EmojiTypeface.create] so the compile contract
 * holds. [ScaledemojiTest] is `@Disabled` because the dispatch
 * throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ScaledemojiGM : GM() {

    override fun getName(): String = "scaledemoji"
    override fun getISize(): SkISize = SkISize.Make(640, 320)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.CBDT, SkData.MakeWithCopy(ByteArray(0)))
    }
}
