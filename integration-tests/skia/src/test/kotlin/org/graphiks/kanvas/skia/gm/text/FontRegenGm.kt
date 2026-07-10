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
 * Port of Skia's `gm/fontregen.cpp::FontRegenGM`.
 * Draws 4 TextBlobs with different fonts to exercise text blob rendering.
 * @see https://github.com/google/skia/blob/main/gm/fontregen.cpp
 */
class FontRegenGm : SkiaGm {
    override val name = "fontregen"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val font80 = Font(typeface, size = 80f, subpixel = false)
        val font162 = Font(typeface, size = 162f, subpixel = false)

        val b0 = font80.toTextBlob("abcdefghijklmnopqrstuvwxyz", 0f, 0f)
        val b1 = font162.toTextBlob("ABCDEFGHI", 0f, 0f)
        val b2 = font162.toTextBlob("NOPQRSTUV", 0f, 0f)

        canvas.drawTextBlob(b0, 10f, 80f, Paint(color = Color.BLACK))
        canvas.drawTextBlob(b1, 10f, 225f, Paint(color = Color.BLACK))
        canvas.drawTextBlob(b0, 10f, 305f, Paint(color = Color(0xFF010101u)))
        canvas.drawTextBlob(b2, 10f, 465f, Paint(color = Color(0xFF010101u)))
    }
}
