package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/mac_aa_explorer.cpp` cross-platform DEF_SIMPLE_GM(macaa_colors, ...).
 * Renders "Hamburgefons" in 4 color combos × 5 font sizes.
 * Note: Font edging/hinting not available on GmCanvas, so edging variants render identically.
 * @see https://github.com/google/skia/blob/main/gm/mac_aa_explorer.cpp
 */
class MacaaColorsGm : SkiaGm {
    override val name = "macaa_colors"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val gray = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f)
        val colors = listOf(
            Color.BLACK to Color.WHITE,
            Color.BLACK to gray,
            Color.WHITE to Color.BLACK,
            Color.WHITE to gray,
        )
        val sizes = listOf(10f, 12f, 15f, 18f, 24f)
        val str = "Hamburgefons"
        val colWidth = 200f

        for ((textColor, bgColor) in colors) {
            canvas.save()
            val bgPaint = Paint(color = bgColor)
            canvas.drawRect(Rect.fromLTRB(0f, 0f, colWidth, 500f), bgPaint)

            var y = 10f
            val x = 10f
            for (ps in sizes) {
                val font = Font(typeface, size = ps)
                for (lcd in listOf(false, true)) {
                    y += font.getMetrics()?.let { -it.ascent + it.descent + it.leading } ?: (ps + 2f)
                    y += 2f
                    val paint = Paint(color = textColor)
                    canvas.drawString(str, x, y, font, paint)
                }
                y += 8f
            }
            canvas.restore()
            canvas.translate(colWidth, 0f)
        }
    }
}
