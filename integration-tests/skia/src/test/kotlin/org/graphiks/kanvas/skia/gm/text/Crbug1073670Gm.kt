package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/crbug_1073670.cpp::crbug_1073670`.
 * Validates text rendering with a linear gradient shader.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1073670.cpp
 */
class Crbug1073670Gm : SkiaGm {
    override val name = "crbug_1073670"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, 250f),
            stops = listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
        )
        val paint = Paint(shader = shader)
        val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
        val font = Font(typeface, size = 325f)
        canvas.drawString("Gradient", 10f, 250f, font, paint)
    }
}
