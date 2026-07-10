package org.graphiks.kanvas.skia.gm.image

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
 * Port of Skia's `gm/colorspace.cpp::DEF_SIMPLE_GM(colorspace2, ...)`.
 * @see https://github.com/google/skia/blob/main/gm/colorspace.cpp
 */
class Colorspace2Gm : SkiaGm {
    override val name = "colorspace2"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 896
    override val height = 640

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawString("colorspace2: not color-managed (sRGB default)", 10f, 20f, font, Paint())

        val w = 128f
        val h = 128f
        val paint = Paint(color = Color(0xFF8040FFu))
        val rect = Rect.fromXYWH(0f, 0f, w, h)

        for (row in 0 until 5) {
            canvas.save()
            for (col in 0 until 7) {
                canvas.drawRect(rect, paint)
                val label = "${'A' + row}${col + 1}"
                canvas.drawString(label, rect.left + 4f, rect.top + h / 2f, font, Paint())
                canvas.translate(w, 0f)
            }
            canvas.restore()
            canvas.translate(0f, h)
        }
    }
}
