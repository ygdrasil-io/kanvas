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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gammatext.cpp`.
 * Lays a vertical black-to-white gradient background then paints
 * "Hamburgefons" in 8 colour columns.
 * @see https://github.com/google/skia/blob/main/gm/gammatext.cpp
 */
class GammatextGm : SkiaGm {
    override val name = "gammatext"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 10.0
    override val width = 1024
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 16f)

        val grad = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, 480f),
            stops = listOf(
                GradientStop(0f, Color.BLACK),
                GradientStop(1f, Color.WHITE),
            ),
            tileMode = TileMode.CLAMP,
        )
        canvas.drawRect(Rect(0f, 0f, 1024f, 480f), Paint(shader = grad))

        val fg = listOf(
            Color.WHITE, Color.fromRGBA(1f, 1f, 0f),
            Color.fromRGBA(1f, 0f, 1f), Color.fromRGBA(0f, 1f, 1f),
            Color.RED, Color.GREEN, Color.BLUE, Color.BLACK,
        )

        val text = "Hamburgefons"
        var x = 10f
        for (color in fg) {
            var y = 40f
            while (y < 480f) {
                canvas.drawString(text, x, y, font, Paint(color = color))
                y += font.size * 2f
            }
            x += 1024f / fg.size
        }
    }
}
