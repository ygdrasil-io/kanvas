package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.skia.math.SkISize

/**
 * R-final.S — **STUB.EMOJI_TABLES** consumer GM. Iso-aligned port of
 * upstream's `gm/scaledemoji_rendering.cpp` (which exercises the
 * scaled-emoji dispatch through every supported colour-glyph
 * format — CBDT, sbix, COLR v0, SVG — to spot bitmap-vs-vector
 * regressions).
 *
 * The body touches [EmojiTypeface.create] under the [EmojiTypeface.Format.Sbix]
 * dispatch so the compile contract holds. [ScaledemojiRenderingTest]
 * is `@Disabled` because the dispatch throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ScaledemojiRenderingGM : GM() {

    override fun getName(): String = "scaledemoji-rendering"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.Sbix, SkData.MakeWithCopy(ByteArray(0)))
    }
}
