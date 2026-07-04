package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/fontscaler.cpp::FontScalerGM` (1450 x 750).
 * Sweeps "Hamburgefons ooo mmm" at sizes 6..22 in five rotated/translated columns.
 * @see https://github.com/google/skia/blob/main/gm/fontscaler.cpp
 */
class FontScalerGm : SkiaGm {
    override val name = "fontscaler"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1450
    override val height = 750

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Hamburgefons ooo mmm"

        for (j in 0 until 2) {
            for (i in 0 until 5) {
                val x = 10f
                var y = 20f

                canvas.save()
                canvas.translate((50 + i * 230).toFloat(), 20f)
                val px = 10f
                val py = 200f
                canvas.translate(px, py)
                canvas.rotate((i * 5).toFloat())
                canvas.translate(-px, -py)

                val p = Paint()
                val r = Rect.fromLTRB(x - 3f, 15f, x - 1f, 280f)
                canvas.drawRect(r, p)

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
