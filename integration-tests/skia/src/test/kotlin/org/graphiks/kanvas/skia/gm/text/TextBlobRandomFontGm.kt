package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/textblobrandomfont.cpp`.
 * Tests text blob rendering with random font sizes and slight rotation.
 * @see https://github.com/google/skia/blob/main/gm/textblobrandomfont.cpp
 */
class TextBlobRandomFontGm : SkiaGm {
    override val name = "textblobrandomfont"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 2000
    override val height = 1600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        val paint = Paint(antiAlias = true, color = Color.fromRGBA(1f, 0f, 1f, 1f))

        canvas.save()
        canvas.rotate(-0.05f)
        val font1 = Font(typeface, size = 32f)
        canvas.drawString("The quick brown fox jumps over the lazy dog.", 10f, 50f, font1, paint)
        val font2 = Font(typeface, size = 160f)
        canvas.drawString("The quick brown fox", 10f, 250f, font2, paint)
        canvas.drawString("jumps over the lazy dog.", 10f, 450f, font2, paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-0.05f)
        canvas.drawString("The quick brown fox jumps over the lazy dog.", 10f, 650f, font1, paint)
        canvas.drawString("The quick brown fox", 10f, 850f, font2, paint)
        canvas.drawString("jumps over the lazy dog.", 10f, 1050f, font2, paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-0.05f)
        canvas.drawString("The quick brown fox jumps over the lazy dog.", 10f, 1250f, font1, paint)
        canvas.drawString("The quick brown fox", 10f, 1450f, font2, paint)
        canvas.drawString("jumps over the lazy dog.", 10f, 1550f, font2, paint)
        canvas.restore()
    }
}
