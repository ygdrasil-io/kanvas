package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.skia.math.SkISize

/**
 * R-final.S — **STUB.EMOJI_TABLES** consumer GM. Iso-aligned port of
 * upstream's `gm/coloremoji_blendmodes.cpp` (which composites a
 * colour-emoji glyph through every supported [SkBlendMode] to make
 * sure the bitmap-emit path interacts correctly with the
 * compositor).
 *
 * The body touches [EmojiTypeface.create] under the
 * [EmojiTypeface.Format.COLRv0] dispatch so the compile contract
 * holds. [ColoremojiBlendmodesTest] is `@Disabled` because the
 * dispatch throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ColoremojiBlendmodesGM : GM() {

    override fun getName(): String = "coloremoji-blendmodes"
    override fun getISize(): SkISize = SkISize.Make(640, 640)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.COLRv0, SkData.MakeWithCopy(ByteArray(0)))
    }
}
