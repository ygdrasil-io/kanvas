package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/stroketext.cpp` (DEF_SIMPLE_GM, 1200 × 480).
 * Draws the letter "P" at 255pt and 257pt in fill+stroke, stroke-only,
 * and stroke-with-dash variants.
 * @see https://github.com/google/skia/blob/main/gm/stroketext.cpp
 */
class StrokeTextGm : SkiaGm {
    override val name = "stroketext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 75.0
    override val width = 1200
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)

        val font = Font(typeface, size = kBelowThreshold_TextSize.toFloat())
        drawTextSet(canvas, paint, font)

        canvas.translate(600f, 0f)
        drawTextSet(canvas, paint, font.copy(size = kAboveThreshold_TextSize.toFloat()))
    }

    private fun drawTextSet(canvas: GmCanvas, paint: Paint, font: Font) {
        canvas.save()
        drawTextStroked(canvas, paint, font, strokeWidth = 10f)

        canvas.translate(200f, 0f)
        drawTextStroked(canvas, paint, font, strokeWidth = 0f)

        val intervals = floatArrayOf(20f, 10f, 5f, 10f)
        canvas.translate(200f, 0f)
        val dashed = paint.copy(pathEffect = PathEffect.Dash(intervals))
        drawTextStroked(canvas, dashed, font, strokeWidth = 10f)

        canvas.restore()
    }

    private fun drawTextStroked(canvas: GmCanvas, paint: Paint, font: Font, strokeWidth: Float) {
        val loc = Point(20f, 435f)

        if (strokeWidth > 0f) {
            val fillPaint = paint.copy(style = PaintStyle.FILL)
            canvas.drawSimpleText("P", loc.x, loc.y - 225f, font, fillPaint)
            canvas.drawTextBlob(makeSingleGlyphBlob("P", loc, font), 0f, 0f, fillPaint)
        }

        val strokePaint = paint.copy(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = strokeWidth,
        )
        canvas.drawSimpleText("P", loc.x, loc.y - 225f, font, strokePaint)
        canvas.drawTextBlob(makeSingleGlyphBlob("P", loc, font), 0f, 0f, strokePaint)
    }

    private fun makeSingleGlyphBlob(text: String, loc: Point, font: Font): TextBlob {
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(loc.x, loc.y))
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = font.size,
        )
    }

    private companion object {
        const val kBelowThreshold_TextSize = 255
        const val kAboveThreshold_TextSize = 257
    }
}
