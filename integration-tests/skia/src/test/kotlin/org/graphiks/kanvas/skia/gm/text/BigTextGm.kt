package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/bigtext.cpp` BigTextGM.
 * Draws "/" and "\" at 1500pt, centred, in red and blue.
 * @see https://github.com/google/skia/blob/main/gm/bigtext.cpp
 */
class BigTextGm : SkiaGm {
    override val name = "bigtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 65.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 1500f)
        val textWidth = font.measureText("/")
        val posX = width / 2f - textWidth / 2f

        // Match Skia's font.measureText(text, &bounds)→pos = height/2 - bounds.centerY()
        // In Kanvas: ascent > 0 (above baseline), descent < 0 (below baseline)
        // Equivalent centerY = -(ascent + descent) / 2 → posY = height/2 - centerY
        val metrics = font.getMetrics()
        val centerY = -((metrics?.ascent ?: 1200f) + (metrics?.descent ?: -300f)) / 2f
        val posY = height / 2f - centerY

        canvas.drawString("/", posX, posY, font, Paint(color = Color.RED))
        canvas.drawString("\\", posX, posY, font, Paint(color = Color.BLUE))
    }
}
