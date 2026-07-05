package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagesource.cpp::ImageSourceGM` (500 x 150).
 * Exercises image source variations (full, subset, scaled).
 * @see https://github.com/google/skia/blob/main/gm/imagesource.cpp
 */
class ImageSourceGm : SkiaGm {
    override val name = "imagesource"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 150

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.BLACK))

        val image = makeStringImage(100, 100, 0xFFFFFFFFu.toInt(), 20f, 70f, 96f, "e")

        val srcRect = Rect.fromXYWH(20f, 20f, 30f, 30f)
        val dstRect = Rect.fromXYWH(0f, 10f, 60f, 60f)
        val clipRect = Rect.fromXYWH(0f, 0f, 100f, 100f)
        val bounds = Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat())

        // Panel 1 - full image, nearest sampler
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.drawImage(image, clipRect, Paint())
        canvas.restore()
        canvas.translate(100f, 0f)

        // Panel 2 - subset -> same size, cubic
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.drawImageRect(image, srcRect, srcRect)
        canvas.restore()
        canvas.translate(100f, 0f)

        // Panel 3 - subset -> dst rect, cubic
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.drawImageRect(image, srcRect, dstRect)
        canvas.restore()
        canvas.translate(100f, 0f)

        // Panel 4 - full -> dst rect, cubic
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.drawImageRect(image, bounds, dstRect)
        canvas.restore()
        canvas.translate(100f, 0f)
    }

    private fun makeStringImage(w: Int, h: Int, color: Int, x: Float, y: Float, textSize: Float, str: String): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val paint = Paint(color = fromArgb(color))
            val font = Font(typeface, size = textSize)
            drawString(str, x, y, font, paint)
        }
        return surface.makeImageSnapshot()
    }

    private fun fromArgb(argb: Int): Color {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
    }
}
