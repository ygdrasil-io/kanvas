package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/coloremoji.cpp::ColorEmojiGM`.
 *
 * Original renders color emoji glyphs through the font dispatch —
 * exercising `SBIX` / `CBDT` / `COLRv0` / `COLRv1` / `SVG` color-emoji
 * tables via FreeType + (lib)rsvg JNI.
 *
 * TODO: missing API — color-emoji font-table dispatch (see
 * API_FINALIZATION_PLAN.md STUB.EMOJI_TABLES). Sibling
 * `ColoremojiBlendmodesGM` already lives here as a `@Disabled` placeholder.
 */
public class ColorEmojiGM : GM() {
    override fun getName(): String = "coloremoji"
    override fun getISize(): SkISize = SkISize.Make(650, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — color-emoji font-table dispatch (SBIX/CBDT/COLR/SVG).
    }
}
