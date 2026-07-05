package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/imagemagnifier.cpp::ImageMagnifierGM` (500 x 500).
 * Draws random text through a magnifier image filter.
 * @see https://github.com/google/skia/blob/main/gm/imagemagnifier.cpp
 */
class ImageMagnifierGm : SkiaGm {
    override val name = "imagemagnifier"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.BLACK))

        val magnifier = ImageFilter.Magnifier(
            src = Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()),
            zoom = 2f, inset = 100f,
        )
        canvas.saveLayer(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(imageFilter = magnifier))
        drawContent(canvas, 300f, 25)
        canvas.restore()
    }

    private fun drawContent(c: GmCanvas, maxTextSize: Float, count: Int) {
        val str = "The quick brown fox jumped over the lazy dog."
        val font = Font(typeface)
        val rng = Random(42)
        repeat(count) {
            val x = rng.nextInt(500)
            val y = rng.nextInt(500)
            val color = Color.fromRGBA(
                rng.nextInt(256) / 255f,
                rng.nextInt(256) / 255f,
                rng.nextInt(256) / 255f,
                1f,
            )
            font.copy(size = rng.nextFloat() * maxTextSize)
            c.drawString(str, x.toFloat(), y.toFloat(), font, Paint(color = color))
        }
    }
}
