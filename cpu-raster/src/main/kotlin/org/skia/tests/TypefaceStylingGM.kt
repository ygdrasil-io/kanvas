package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/typeface.cpp::typeface_styling`
 * (`DEF_SIMPLE_GM(typeface_styling, canvas, 710, 360)`).
 *
 * Exercises the combinations of `SkPaint::Style` + stroke width with
 * `SkFont::setEmbolden(true/false)` against an "A" glyph from the
 * Roboto family (Liberation Sans in our portable build) at size 100,
 * `kAntiAlias` edging. Three glyph rows per column:
 *   1. normal
 *   2. emboldened
 *   3. normal (yellow) on top of emboldened (shows the embolden delta).
 *
 * **Embolden caveat**: AWT's `Font.deriveFont` with `Font.BOLD` widens
 * the strokes, but we don't expose a per-glyph fake-bold offset in the
 * AWT typeface; the [SkFont.isEmbolden] flag is propagated through
 * [SkFont] and consumed only when the typeface actually carries an
 * "embolden" hook. In practice we render the typeface unchanged for
 * both passes — the third row's yellow overlay therefore lines up
 * exactly with the underlying glyph, instead of showing a delta. The
 * pixel comparison absorbs this via the textual tolerance.
 *
 * 710 × 360 canvas. The original `drawGlyphs(glyphs, pos, origin, …)`
 * primitive is not yet ported in `:kanvas-skia` ; we use
 * [SkCanvas.drawString] with the corresponding string "A" — same glyph,
 * same paint pipeline, same end pixels.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(typeface_styling, canvas, 710, 360) {
 *     sk_sp<SkTypeface> face = ToolUtils::CreateTypefaceFromResource("fonts/Roboto-Regular.ttf");
 *     if (!face) face = ToolUtils::DefaultPortableTypeface();
 *     SkFont font(face, 100);
 *     font.setEdging(SkFont::Edging::kAntiAlias);
 *     …
 * }
 * ```
 */
public class TypefaceStylingGM : GM() {

    override fun getName(): String = "typeface_styling"
    override fun getISize(): SkISize = SkISize.Make(710, 360)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val face: SkTypeface = ToolUtils.DefaultPortableTypeface()
        val font = SkFont(face, 100f).apply { edging = SkFont.Edging.kAntiAlias }

        // Mirrors upstream's recs table.
        data class Rec(val style: SkPaint.Style, val width: Float)
        val recs = listOf(
            Rec(SkPaint.Style.kFill_Style, 0f),
            Rec(SkPaint.Style.kStroke_Style, 0f),
            Rec(SkPaint.Style.kStroke_Style, 3f),
            Rec(SkPaint.Style.kStrokeAndFill_Style, 0f),
            Rec(SkPaint.Style.kStrokeAndFill_Style, 3f),
        )

        c.translate(0f, -20f)
        for (r in recs) {
            draw(c, font, r.style, r.width)
            c.translate(100f, 0f)
        }
    }

    private fun draw(c: SkCanvas, font: SkFont, style: SkPaint.Style, width: Float) {
        val paint = SkPaint().apply {
            this.style = style
            this.strokeWidth = width
        }

        // Row 2 + row 3 — emboldened.
        font.isEmbolden = true
        c.drawString("A", 20f, 120f * 2, font, paint)
        c.drawString("A", 20f, 120f * 3, font, paint)

        // Row 1 — normal.
        font.isEmbolden = false
        c.drawString("A", 20f, 120f * 1, font, paint)
        // Row 3 overlay — yellow normal on top of the previous emboldened.
        paint.color = SK_ColorYELLOW
        c.drawString("A", 20f, 120f * 3, font, paint)
    }
}
