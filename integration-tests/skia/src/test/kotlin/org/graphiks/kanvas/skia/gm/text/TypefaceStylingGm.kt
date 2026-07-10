package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/typeface.cpp::typeface_styling` (710 × 360).
 * Draws "A" glyphs in 5 columns × 3 rows exercising fill/stroke/
 * stroke-and-fill paint styles. Embolden is not yet exposed on Font.
 * @see https://github.com/google/skia/blob/main/gm/typeface.cpp
 */
class TypefaceStylingGm : SkiaGm {
    override val name = "typeface_styling"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 710
    override val height = 360

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 100f)

        data class Rec(val style: PaintStyle, val strokeWidth: Float)
        val recs = listOf(
            Rec(PaintStyle.FILL, 0f),
            Rec(PaintStyle.STROKE, 0f),
            Rec(PaintStyle.STROKE, 3f),
            Rec(PaintStyle.STROKE_AND_FILL, 0f),
            Rec(PaintStyle.STROKE_AND_FILL, 3f),
        )

        canvas.translate(0f, -20f)
        for (r in recs) {
            drawColumn(canvas, font, r.style, r.strokeWidth)
            canvas.translate(100f, 0f)
        }
    }

    private fun drawColumn(canvas: GmCanvas, font: Font, style: PaintStyle, strokeWidth: Float) {
        val paint = Paint(style = style, strokeWidth = strokeWidth)

        // Row 2 — normal
        canvas.drawString("A", 20f, 120f * 2, font, paint)
        // Row 3 — normal (same glyph, no embolden available)
        canvas.drawString("A", 20f, 120f * 3, font, paint)
        // Row 1 — normal
        canvas.drawString("A", 20f, 120f * 1, font, paint)
        // Row 3 overlay — yellow
        canvas.drawString("A", 20f, 120f * 3, font, paint.copy(color = Color.fromRGBA(1f, 1f, 0f, 1f)))
    }
}
