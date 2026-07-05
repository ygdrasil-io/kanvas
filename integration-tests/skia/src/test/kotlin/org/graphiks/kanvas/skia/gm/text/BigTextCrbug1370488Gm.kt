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
 * Port of Skia's `gm/bigtext.cpp` (bigtext_crbug_1370488).
 * Regression test for crbug.com/1370488: large glyph rendering.
 * Falls back to Liberation Sans + "H" when SpiderSymbol.ttf is absent.
 * @see https://github.com/google/skia/blob/main/gm/bigtext.cpp
 */
class BigTextCrbug1370488Gm : SkiaGm {
    override val name = "bigtext_crbug_1370488"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 5.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 12f)
        canvas.translate(-1800f, 1800f)
        canvas.scale(437.5f, 437.5f)
        canvas.drawString("H", 0f, 0f, font, Paint(antiAlias = true, color = Color.BLACK))
    }
}
