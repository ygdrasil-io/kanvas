package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32

/**
 * Port of Skia's `gm/crbug_1073670.cpp::crbug_1073670`.
 * Validates text rendering with a linear gradient shader.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1073670.cpp
 */
class Crbug1073670Gm : SkiaGm {
    override val name = "crbug_1073670"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(0f, 250f),
            stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        )
        val paint = Paint(shader = shader)
        val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
        val font = Font(typeface, size = 325f)
        canvas.drawString("Gradient", 10f, 250f, font, paint)
    }
}
