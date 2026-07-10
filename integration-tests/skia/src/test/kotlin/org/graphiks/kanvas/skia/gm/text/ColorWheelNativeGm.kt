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
 * Port of Skia's `gm/colorwheel.cpp::colorwheelnative`.
 * @see https://github.com/google/skia/blob/main/gm/colorwheel.cpp
 */
class ColorWheelNativeGm : SkiaGm {
    override val name = "colorwheelnative"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 28

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 18f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0.753f, 0.753f, 0.753f, 1f)
        canvas.drawString("R",  8f, 20f, font, Paint(color = Color.RED))
        canvas.drawString("G", 24f, 20f, font, Paint(color = Color(0xFF00FF00u)))
        canvas.drawString("B", 40f, 20f, font, Paint(color = Color(0xFF0000FFu)))
        canvas.drawString("C", 56f, 20f, font, Paint(color = Color(0xFF00FFFFu)))
        canvas.drawString("M", 72f, 20f, font, Paint(color = Color(0xFFFF00FFu)))
        canvas.drawString("Y", 88f, 20f, font, Paint(color = Color(0xFFFFFF00u)))
        canvas.drawString("K", 104f, 20f, font, Paint(color = Color.BLACK))
    }
}
