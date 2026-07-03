package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gammatext.cpp` (gammatext_color_shader).
 * Three columns of "ABCDEFG" rendered with progressively-brightening grey:
 * plain colour, constant-colour shader, and F32 shader (no color space).
 * @see https://github.com/google/skia/blob/main/gm/gammatext.cpp
 */
class GammatextColorShaderGm : SkiaGm {
    override val name = "gammatext_color_shader"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 275

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0.533f, 0.533f, 0.533f)),
        )
        val kText = "ABCDEFG"
        val font = Font(typeface, size = 18f, antiAlias = true, subpixel = true)

        canvas.translate(10f, 30f)
        var i = 0
        while (i < 256) {
            val frac = i / 255f
            val color = Color.fromRGBA(frac, frac, frac)
            canvas.drawString(kText, 0f, 0f, font, Paint(color = color))
            canvas.drawString(kText, 100f, 0f, font, Paint(shader = Shader.SolidColor(color)))
            canvas.drawString(kText, 200f, 0f, font, Paint(shader = Shader.SolidColor(color)))
            canvas.translate(0f, 20f)
            i += 20
        }
    }
}
