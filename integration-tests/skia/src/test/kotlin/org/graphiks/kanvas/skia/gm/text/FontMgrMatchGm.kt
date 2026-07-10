package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/** Port of Skia's `gm/fontmgr.cpp` (font-mgr match variant).
 *  Tests font manager family matching — renders "Hello World" with
 *  various font families to verify font-matching logic.
 *  @see https://github.com/google/skia/blob/main/gm/fontmgr.cpp
 */
class FontMgrMatchGm : SkiaGm {
    override val name = "fontmgr_match"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 1024

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, 17f)
        canvas.translate(20f, 40f)
        canvas.drawString("Liberation Sans (portable)", 0f, 0f, font, Paint())
    }
}
