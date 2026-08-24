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
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/gradtext.cpp` ChromeGradText1GM.
 * Clipped 100x100 region with giant "I" and green gradient shader.
 * @see https://github.com/google/skia/blob/main/gm/gradtext.cpp
 */
class ChromeGradText1Gm : SkiaGm {
    override val name = "chrome_gradtext1"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 90.0
    override val width = 500
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = RectF32(0f, 0f, 100f, 100f)
        canvas.clipRect(r)
        canvas.drawRect(r, Paint(color = ColorARGB.Red))

        val paint = Paint(shader = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(1f, 0f),
            stops = listOf(
                GradientStop(0f, ColorARGB.Green),
                GradientStop(1f, ColorARGB.Green),
            ),
            tileMode = TileMode.CLAMP,
        ))
        val font = Font(typeface, size = 500f, antiAlias = false)
        canvas.drawString("I", 0f, 100f, font, paint)
    }
}
