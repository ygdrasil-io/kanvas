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
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/glyph_pos.cpp` (six variants, 800 × 600 each).
 * Renders text six times at progressively spicier CTMs.
 * @see https://github.com/google/skia/blob/main/gm/glyph_pos.cpp
 */
abstract class GlyphPosGm(
    private val variantName: String,
    private val strokeWidth: Float,
    private val strokeStyle: PaintStyle,
) : SkiaGm {
    override val name: String get() = variantName
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(40f, 40f, 41f, 41f), Paint())

        canvas.translate(50f, 50f)
        drawTestCase(canvas, 1f, drawRef = true)
        canvas.translate(0f, 50f)
        drawTestCase(canvas, 3f, drawRef = true)

        canvas.translate(0f, 100f)
        canvas.save()
        canvas.scale(3f, 3f)
        drawTestCase(canvas, 1f, drawRef = false)
        canvas.restore()

        canvas.translate(0f, 100f)
        canvas.save()
        canvas.scale(3f, 6f)
        drawTestCase(canvas, 1f, drawRef = false)
        canvas.restore()

        canvas.translate(0f, 80f)
        canvas.save()
        canvas.scale(3f, 3f)
        canvas.concat(Matrix33.makeAll(1f, 8f / 25f, 0f, 2f / 25f, 1f, 0f))
        drawTestCase(canvas, 1f, drawRef = false)
        canvas.restore()

        canvas.translate(0f, 80f)
        canvas.save()
        canvas.concat(Matrix33.makeAll(
            1f, 8f / 25f, 0f,
            2f / 25f, 1f, 0f,
            -1f / 340f, 0f, 1f,
        ))
        drawTestCase(canvas, 1f, drawRef = false)
        canvas.restore()
    }

    private fun drawTestCase(canvas: GmCanvas, textScale: Float, drawRef: Boolean) {
        var paint = Paint(
            color = Color.BLACK,
            antiAlias = true,
            strokeWidth = strokeWidth,
            style = strokeStyle,
        )

        val font = Font(typeface, kTextHeight * textScale)

        if (drawRef) {
            paint = paint.copy(
                style = PaintStyle.STROKE,
                strokeWidth = 0f,
                color = Color.GREEN,
            )
            val bounds = Rect(0f, -font.size * 0.3f, font.size * kText.length * 0.6f, font.size * 0.3f)
            canvas.drawRect(bounds, paint)

            val advance = font.size * kText.length * 0.45f
            paint = paint.copy(color = Color.RED)
            canvas.drawLine(0f, 0f, advance, 0f, paint)
        }

        paint = paint.copy(
            color = Color.BLACK,
            strokeWidth = strokeWidth,
            style = strokeStyle,
        )
        canvas.drawString(kText, 0f, 0f, font, paint)

        if (drawRef) {
            paint = paint.copy(
                style = PaintStyle.STROKE,
                strokeWidth = 0f,
                color = Color(0xFFFF00FFu),
            )
            var w = 0f
            for (i in kText.indices) {
                canvas.drawLine(w, 0f, w, 5f, paint)
                w += font.size * 0.45f
            }
        }
    }

    private companion object {
        private const val kTextHeight = 14f
        private const val kText = "Proportional Hamburgefons #% fi"
    }
}

class GlyphPosHbGm : GlyphPosGm("glyph_pos_h_b", 0f, PaintStyle.STROKE_AND_FILL)
class GlyphPosNbGm : GlyphPosGm("glyph_pos_n_b", 1.2f, PaintStyle.STROKE_AND_FILL)
class GlyphPosHsGm : GlyphPosGm("glyph_pos_h_s", 0f, PaintStyle.STROKE)
class GlyphPosNsGm : GlyphPosGm("glyph_pos_n_s", 1.2f, PaintStyle.STROKE)
class GlyphPosHfGm : GlyphPosGm("glyph_pos_h_f", 0f, PaintStyle.FILL)
class GlyphPosNfGm : GlyphPosGm("glyph_pos_n_f", 1.2f, PaintStyle.FILL)
