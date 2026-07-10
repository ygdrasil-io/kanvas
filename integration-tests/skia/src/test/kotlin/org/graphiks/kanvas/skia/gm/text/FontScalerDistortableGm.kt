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

/**
 * Port of Skia's `gm/fontscalerdistortable.cpp::FontScalerDistortableGM`
 * (`fontscalerdistortable`, 550 x 700).
 *
 * Renders a 2 x 5 grid of typeface instances at varying sizes 6..22.
 * In Skia this exercises variable font axis cloning; in Kanvas the
 * same structure is preserved without variable support.
 * @see https://github.com/google/skia/blob/main/gm/fontscalerdistortable.cpp
 */
class FontScalerDistortableGm : SkiaGm {
    override val name = "fontscalerdistortable"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 550
    override val height = 700

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "abc"
        val rows = 2
        val cols = 5

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = 10f
                var y = 20f

                canvas.save()
                canvas.translate(30f + col * 100f, 20f)
                val px = 10f
                val py = 200f
                canvas.translate(px, py)
                canvas.rotate((col * 5).toFloat())
                canvas.translate(-px, -py)

                val barPaint = Paint()
                val r = Rect.fromLTRB(x - 3f, 15f, x - 1f, 280f)
                canvas.drawRect(r, barPaint)

                var ps = 6
                while (ps <= 22) {
                    val font = Font(typeface, size = ps.toFloat())
                    canvas.drawString(text, x, y, font, Paint())
                    y += font.getMetrics()?.let { -it.ascent + it.descent } ?: font.size * 1.2f
                    ps++
                }

                canvas.restore()
            }
            canvas.translate(0f, 360f)
        }
    }
}
