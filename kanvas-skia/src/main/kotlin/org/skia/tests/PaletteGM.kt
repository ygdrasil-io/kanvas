package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkTypeface
import org.skia.foundation.colr.SkColrV1
import org.skia.math.SkISize

/**
 * R-final.S — **STUB.COLR_V1** consumer GM. Iso-aligned port of
 * upstream's `gm/palette.cpp` (which renders a colour-emoji
 * glyph through every entry of the font's `CPAL` table, plus a
 * couple of [SkFontArguments.Palette.overrides] cases).
 *
 * The body assembles a [SkFontArguments.Palette] (compile-pinned
 * surface) and touches [SkColrV1.makeColrV1Glyphs]. [PaletteTest]
 * is `@Disabled` because the dispatch throws `STUB.COLR_V1`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.COLR_V1.
 */
public class PaletteGM : GM() {

    override fun getName(): String = "palette"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        // Compile-pin the SkFontArguments.Palette surface.
        val args = SkFontArguments(
            palette = SkFontArguments.Palette(
                index = 1,
                overrides = listOf(
                    SkFontArguments.PaletteOverride(index = 0, color = SK_ColorRED),
                ),
            ),
        )
        // Silence "unused" lint without leaking lazy-init oddities.
        @Suppress("UNUSED_VARIABLE") val _kept = args
        // Touch the stubbed dispatch — throws STUB.COLR_V1 at runtime.
        SkColrV1.makeColrV1Glyphs(SkTypeface.MakeEmpty(), shortArrayOf(0))
    }
}
