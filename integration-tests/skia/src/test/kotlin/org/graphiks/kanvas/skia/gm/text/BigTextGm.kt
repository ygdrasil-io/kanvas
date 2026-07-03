package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
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
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 1500f)
        val textWidth = font.measureText("/")
        val posX = width / 2f - textWidth / 2f
        val posY = height / 2f + 1500f / 3f

        canvas.drawString("/", posX, posY, font, Paint(color = Color.RED))
        canvas.drawString("\\", posX, posY, font, Paint(color = Color.BLUE))
    }
}
