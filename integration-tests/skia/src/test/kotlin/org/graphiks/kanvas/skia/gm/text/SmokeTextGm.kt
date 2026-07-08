package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/** Basic smoke test for text rendering — draws a simple string "Hello"
 *  at a fixed position using Liberation Sans to verify basic text output.
 */
class SmokeTextGm : SkiaGm {
    override val name = "smoke_text"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 100

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 24f,
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawString("Hello Kanvas", 10f, 40f, font, Paint(color = Color.BLACK))
        canvas.drawString("GM Text", 10f, 80f, font, Paint(color = Color.RED))
    }
}
