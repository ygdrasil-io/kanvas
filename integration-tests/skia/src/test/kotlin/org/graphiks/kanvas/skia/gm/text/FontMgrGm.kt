package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

class FontMgrGm : SkiaGm {
    override val name = "fontmgr_iter"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1536
    override val height = 768

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, 17f)
        var y = 20f
        val families = listOf("Liberation Sans", "Liberation Serif", "Liberation Mono")
        for (family in families) {
            canvas.drawString(family, 20f, y, font, Paint())
            y += 24f
        }
    }
}
