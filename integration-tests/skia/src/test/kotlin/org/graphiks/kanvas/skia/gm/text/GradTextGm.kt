package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradtext.cpp`.
 * Renders "When in the course of human events" with gradient shaders
 * across font edging modes.
 * @see https://github.com/google/skia/blob/main/gm/gradtext.cpp
 */
class GradTextGm : SkiaGm {
    override val name = "gradtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 77.3
    override val width = 500
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val textSize = 26f
        val font = Font(typeface, size = textSize)

        canvas.drawRect(Rect(0f, 0f, 500f, 240f), Paint())
        canvas.translate(20f, textSize)

        val paints = arrayOf(
            Paint(shader = makeGrad(80f)),
            Paint(shader = makeGrad2(80f)),
        )
        val fonts = arrayOf(
            font.copy(antiAlias = false, subpixel = false),
            font.copy(antiAlias = true, subpixel = false),
            font.copy(antiAlias = true, subpixel = true),
        )
        for (i in 0..1) {
            for (paint in paints) {
                for (f in fonts) {
                    canvas.drawString("When in the course of human events", 0f, 0f, f, paint)
                    canvas.translate(0f, textSize * 4f / 3f)
                }
                canvas.translate(0f, textSize * 2f / 3f)
            }
        }
    }

    private fun makeGrad(width: Float) = Shader.LinearGradient(
        start = Point(0f, 0f),
        end = Point(width, 0f),
        stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 0f)),
            GradientStop(1f, Color.BLUE),
        ),
        tileMode = TileMode.MIRROR,
    )

    private fun makeGrad2(width: Float) = Shader.LinearGradient(
        start = Point(0f, 0f),
        end = Point(width, 0f),
        stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(0.5f, Color.GREEN),
            GradientStop(1f, Color.BLUE),
        ),
        tileMode = TileMode.MIRROR,
    )
}
