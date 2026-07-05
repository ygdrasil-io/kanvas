package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/gradtext.cpp` ChromeGradText2GM.
 * 4 lines: normal fill/stroke and gradient fill/stroke text.
 * @see https://github.com/google/skia/blob/main/gm/gradtext.cpp
 */
class ChromeGradText2Gm : SkiaGm {
    override val name = "chrome_gradtext2"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 95.0
    override val width = 500
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 24f, antiAlias = false)

        canvas.drawString("Normal Fill Text", 0f, 50f, font, Paint(color = Color.BLACK))
        canvas.drawString("Normal Stroke Text", 0f, 100f, font, Paint(
            color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 1f,
        ))

        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(1f, 0f),
            stops = listOf(
                GradientStop(0f, Color.GREEN),
                GradientStop(1f, Color.GREEN),
            ),
            tileMode = TileMode.CLAMP,
        )

        canvas.drawString("Gradient Fill Text", 0f, 150f, font, Paint(shader = shader))
        canvas.drawString("Gradient Stroke Text", 0f, 200f, font, Paint(
            shader = shader, style = PaintStyle.STROKE, strokeWidth = 1f,
        ))
    }
}
