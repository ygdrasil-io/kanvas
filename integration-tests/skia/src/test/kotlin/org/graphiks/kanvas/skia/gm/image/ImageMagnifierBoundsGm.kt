package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/imagemagnifier.cpp::ImageMagnifierBounds` (768 x 512).
 * Demonstrates magnifier filter bounds handling with two rows of three columns.
 * @see https://github.com/google/skia/blob/main/gm/imagemagnifier.cpp
 */
class ImageMagnifierBoundsGm : SkiaGm {
    override val name = "imagemagnifier_bounds"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 768
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawRow(canvas, 16f)
        canvas.translate(0f, 256f)
        drawRow(canvas, 0f)
    }

    private fun drawRow(c: GmCanvas, inset: Float) {
        val widgetBounds = Rect(16f, 24f, 220f, 248f)
        val kZoomAmount = 2.5f
        val kOutBounds = Rect(0f, 0f, 256f, 256f)

        val magnifier: ImageFilter = ImageFilter.Magnifier(
            src = widgetBounds, zoom = kZoomAmount, inset = inset,
        )

        // Backdrop-filter column
        c.save()
        c.clipRect(kOutBounds)
        drawContent(c, 32f, 350)
        c.saveLayer(kOutBounds, Paint(imageFilter = magnifier))
        c.restore()
        drawBorder(c, widgetBounds, Color.BLACK, 2f, 0f)
        if (inset > 0f) {
            drawBorder(c, kOutBounds, Color.RED, 2f, inset)
        }
        c.restore()

        // Regular-filter column
        c.save()
        c.translate(256f, 0f)
        c.clipRect(kOutBounds)
        c.saveLayer(kOutBounds, Paint(imageFilter = magnifier))
        drawContent(c, 32f, 350)
        c.restore()
        drawBorder(c, widgetBounds, Color.BLACK, 2f, 0f)
        if (inset > 0f) {
            drawBorder(c, kOutBounds, Color.RED, 2f, inset)
        }
        c.restore()

        // Un-filtered column
        c.save()
        c.translate(512f, 0f)
        c.clipRect(kOutBounds)
        drawContent(c, 32f, 350)
        drawBorder(c, widgetBounds, Color.BLACK, 2f, 0f)
        c.restore()
    }

    private fun drawBorder(c: GmCanvas, rect: Rect, color: Color, width: Float, borderInset: Float) {
        val r = Rect(
            rect.left + borderInset, rect.top + borderInset,
            rect.right - borderInset, rect.bottom - borderInset,
        )
        val rr = RRect(r, borderInset)
        c.drawRRect(rr, Paint(style = PaintStyle.STROKE, strokeWidth = width, color = color))
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
            c.drawString(str, x.toFloat(), y.toFloat(), font.copy(size = rng.nextFloat() * maxTextSize), Paint(color = color))
        }
    }
}
