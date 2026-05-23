package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.graphiks.math.SkISize

/**
 * STUB.EMOJI_TABLES consumer GM. Iso-aligned port of upstream's
 * `gm/scaledemoji_perspective.cpp::ScaledEmojiPerspectiveGM`
 * (registered as `scaledemojiperspective_<font-format>`, 950 x 950),
 * which composites a colour-emoji glyph through a non-trivial
 * 3 x 3 perspective `SkMatrix` to spot bitmap-scaler-vs-distorted-
 * sampler dispatch issues.
 *
 * The body touches [EmojiTypeface.create] under the
 * [EmojiTypeface.Format.COLRv0] dispatch so the compile contract
 * holds. The matching test is `@Disabled` because the dispatch
 * throws `STUB.EMOJI_TABLES`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * STUB.EMOJI_TABLES.
 */
public class ScaledEmojiPerspectiveGM : GM() {

    override fun getName(): String = "scaledemojiperspective"
    override fun getISize(): SkISize = SkISize.Make(950, 950)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch -- throws STUB.EMOJI_TABLES at runtime.
        EmojiTypeface.create(EmojiTypeface.Format.COLRv0, SkData.MakeWithCopy(ByteArray(0)))
    }
}
