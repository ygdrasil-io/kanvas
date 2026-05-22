package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/palette.cpp::FontPaletteGM` (200 × 200).
 *
 * Renders a COLRv1 emoji typeface ("Bungee Color") under each of its
 * built-in CPAL palettes (`default`, `light`, `dark`, `all-one`,
 * etc.) — exercises the per-glyph palette lookup through
 * `SkFontArguments::Palette`.
 *
 * **API gap** : `:kanvas-skia` has no `SkFontArguments::Palette`
 * support yet. The COLR v0 fallback would draw an outline-only
 * version of each glyph, which does not match the reference
 * `font_palette_*.png`. Stub keeps the class registered ; tests are
 * `@Ignore`'d.
 */
public class FontPaletteGM : GM() {
    override fun getName(): String = "font_palette_default"
    override fun getISize(): SkISize = SkISize.Make(200, 200)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once SkFontArguments.Palette is wired through
        //   SkTypeface.makeClone(args).
    }
}
