package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.emoji.EmojiTypeface
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.EMOJI_TABLES** consumer GM. Iso-aligned port of
 * upstream's `gm/coloremoji_blendmodes.cpp`.
 *
 * Upstream registers five `ColorEmojiBlendModesGM` instances — one per
 * colour-emoji glyph-table dialect — each compositing a single emoji
 * glyph through all 29 [SkBlendMode]s to verify that the bitmap-emit
 * path interacts correctly with the compositor.
 *
 * The five upstream GMs and their canonical names:
 *  - `coloremoji_blendmodes_colrv0` — OpenType COLR v0 layers
 *  - `coloremoji_blendmodes_sbix`   — Apple sbix PNG glyphs
 *  - `coloremoji_blendmodes_cbdt`   — Google CBDT/CBLC PNG bitmap glyphs
 *  - `coloremoji_blendmodes_test`   — Synthetic test emoji font
 *  - `coloremoji_blendmodes_svg`    — OpenType SVG table
 *
 * This class represents all five variants. The body touches
 * [EmojiTypeface.create] so the compile contract against the
 * [EmojiTypeface.Format] surface holds. [ColoremojiBlendmodesTest]
 * is `@Disabled` because [EmojiTypeface.create] throws
 * `STUB.EMOJI_TABLES` at runtime — colour-emoji table dispatch requires
 * FreeType (and librsvg for the SVG variant) via JNI.
 *
 * Upstream canvas size : 400 × 640 (5 columns × 29 blend modes × 64 px each
 * with 10 px spacing + 20 px top margin).
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ColoremojiBlendmodesGM(
    private val format: EmojiTypeface.Format = EmojiTypeface.Format.COLRv0,
) : GM() {

    override fun getName(): String =
        "coloremoji_blendmodes_${format.name.lowercase()}"

    // Upstream: { 400, 640 }  (see gm/coloremoji_blendmodes.cpp::getISize)
    override fun getISize(): SkISize = SkISize.Make(400, 640)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.EMOJI_TABLES at runtime.
        // All five format variants (ColrV0, Sbix, CBDT, SVG, Test) are
        // represented via the [format] constructor parameter.
        EmojiTypeface.create(format, SkData.MakeWithCopy(ByteArray(0)))
    }
}
