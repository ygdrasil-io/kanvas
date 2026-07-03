package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/gammatext.cpp` GammaShaderTextGM.
 * 3 rows of text with solid colour, colour shader, and horizontal fade.
 * @see https://github.com/google/skia/blob/main/gm/gammatext.cpp
 */
class GammashadertextGm : SkiaGm {
    override val name = "gammagradienttext"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
    private val baseColors = listOf(Color.BLACK, Color.RED, Color.BLUE)
    private val shaders = baseColors.map { color ->
        Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(240f, 0f),
            stops = listOf(
                GradientStop(0f, color),
                GradientStop(1f, Color.fromRGBA(
                    color.packed.toFloat(), 0f, 0f, 0f,
                )),
            ),
            tileMode = TileMode.CLAMP,
        )
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 18f)
        val text = "Now is the time for all good"

        for ((i, color) in baseColors.withIndex()) {
            canvas.drawString(text, 10f, 20f, font, Paint(color = color))
            canvas.drawString(text, 10f, 40f, font, Paint(color = color))
            canvas.drawString(text, 10f, 60f, font, Paint(color = color, shader = shaders[i]))
            canvas.translate(0f, 80f)
        }
    }
}
