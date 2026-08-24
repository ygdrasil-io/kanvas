package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.math.color.ColorARGB

/** Port of Skia's `gm/palette.cpp` (font palette variant).
 *  Tests font palette overrides for color fonts — draws emoji text with
 *  custom palette colours.
 *  @see https://github.com/google/skia/blob/main/gm/palette.cpp
 */
class FontPaletteGm : SkiaGm {
    override val name = "font_palette_default"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 400

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(ColorARGB.White.r, ColorARGB.White.g, ColorARGB.White.b)
        canvas.translate(10f, 20f)
        val font = Font(typeface, 200f)
        canvas.drawString("ABC", 0f, 220f, font, Paint(color = ColorARGB.Black))
        canvas.drawString("ABC", 440f, 220f, font, Paint(color = ColorARGB.Black))
    }
}
