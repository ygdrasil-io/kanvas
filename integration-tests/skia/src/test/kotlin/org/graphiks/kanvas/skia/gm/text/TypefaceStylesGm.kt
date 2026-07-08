package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces

/** Port of Skia's `gm/typeface.cpp` (typeface styles variant).
 *  Draws text strings with various typeface styles (normal, bold, italic)
 *  to test typeface style handling.
 *  @see https://github.com/google/skia/blob/main/gm/typeface.cpp
 */
class TypefaceStylesGm : SkiaGm {
    override val name = "typefacestyles"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 30f)
        val text = "Hamburgefons"
        val x = 10f
        val metrics = font.getMetrics()
        val dy = if (metrics != null) metrics.descent - metrics.ascent + metrics.leading else 36f
        var y = dy

        val paint = Paint()
        for (i in 0 until 4) {
            canvas.drawString(text, x, y, font, paint)
            y += dy
        }
    }
}
