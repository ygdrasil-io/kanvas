package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of the cross-platform `DEF_SIMPLE_GM(macaa_colors, canvas, 800,
 * 500)` block at the bottom of upstream's
 * [`gm/mac_aa_explorer.cpp`](https://github.com/google/skia/blob/main/gm/mac_aa_explorer.cpp)
 * (after the `#endif // SK_BUILD_FOR_MAC` guard ; the preceding
 * `MacAAFontsGM` is macOS-only and stays unported).
 *
 * Renders the string `"Hamburgefons"` in four colour combinations
 * (black-on-white, black-on-grey, white-on-black, white-on-grey),
 * each combination drawn as a 5-row × 4-config matrix :
 *
 *  - **rows** : font sizes 10, 12, 15, 18, 24 pt.
 *  - **per-row** : 4 configs combining `edging ∈ {kAntiAlias,
 *    kSubpixelAntiAlias}` × `hinting ∈ {kNone, kNormal}`.
 *
 * Each colour-combo column is 200 px wide ; canvas is 800 × 500.
 *
 * **Adaptations vs upstream** :
 *  - **Typeface choice** — `ToolUtils::CreateTestTypeface("Times", …)`
 *    falls through to [ToolUtils.DefaultPortableTypeface] when the
 *    portable mgr has no "Times" family alias. `:kanvas-skia` ships
 *    Liberation Serif as the closest portable Times analogue ; the
 *    glyph shapes match upstream's portable Times within AWT/FreeType
 *    edge AA drift.
 *  - **Subpixel AA** — `:kanvas-skia` silently downgrades
 *    [SkFont.Edging.kSubpixelAntiAlias] to `kAntiAlias` (no LCD
 *    subpixel mask path on the raster pipeline). The two `lcd ∈
 *    {false, true}` rows therefore render identically here ; on
 *    upstream they differ by the LCD-RGB fringe pattern.
 *  - **Hinting** — both `kNone` and `kNormal` route through AWT's
 *    scaler unchanged ; the AWT glyph cache absorbs hinting hints
 *    silently. The two `hinting ∈ {kNone, kNormal}` per-row entries
 *    therefore render identically here too.
 *
 * Per-row spacing is `font.getSpacing() + 2` (pre-config-switch)
 * with an extra 8-px gutter between size groups, exactly as upstream.
 *
 * Reference : `macaa_colors.png` (800 × 500).
 */
public class MacaaColorsGM : GM() {

    override fun getName(): String = "macaa_colors"
    override fun getISize(): SkISize = SkISize.Make(800, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val gray = 0xFF808080.toInt()
        // Colours array : pairs of (text, background) ; matches upstream's
        // `colors[]` layout (4 pairs interleaved as `text, bg`).
        val colors = intArrayOf(
            SK_ColorBLACK, SK_ColorWHITE,
            SK_ColorBLACK, gray,
            SK_ColorWHITE, SK_ColorBLACK,
            SK_ColorWHITE, gray,
        )
        val sizes = floatArrayOf(10f, 12f, 15f, 18f, 24f)

        val width = 200f
        val height = 500f
        val str = "Hamburgefons"
        val len = str.length

        // Use ToolUtils.CreatePortableTypeface("Times", default style) ;
        // falls back to the portable default if the alias is absent.
        val face = ToolUtils.CreatePortableTypeface("Times", SkFontStyle.Normal())
        val font = SkFont(face, 12f)

        var i = 0
        while (i < colors.size) {
            c.save()

            val paint = SkPaint().apply { color = colors[i + 1] }
            c.drawRect(SkRect.MakeLTRB(0f, 0f, width, height), paint)
            paint.color = colors[i]

            var y = 10f
            val x = 10f
            for (ps in sizes) {
                font.size = ps
                for (lcd in booleanArrayOf(false, true)) {
                    font.edging = if (lcd) SkFont.Edging.kSubpixelAntiAlias
                    else SkFont.Edging.kAntiAlias
                    for (h in arrayOf(SkFontHinting.kNone, SkFontHinting.kNormal)) {
                        font.hinting = h
                        y += font.getSpacing() + 2f
                        c.drawSimpleText(
                            str, len, SkTextEncoding.kUTF8, x, y, font, paint,
                        )
                    }
                }
                y += 8f
            }
            c.restore()
            c.translate(width, 0f)
            i += 2
        }
    }
}
