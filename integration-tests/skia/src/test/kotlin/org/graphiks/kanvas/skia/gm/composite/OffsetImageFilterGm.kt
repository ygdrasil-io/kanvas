package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
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
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/offsetimagefilter.cpp` (600 × 100, black background).
 * Exercises ImageFilter.Offset with various crop rectangles.
 * @see https://github.com/google/skia/blob/main/gm/offsetimagefilter.cpp
 */
class OffsetImageFilterGm : SkiaGm {
    override val name = "offsetimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 100

    private val bitmap: Image by lazy { makeStringImage(80, 80, Color(0xFFD000D0u), "e") }
    private val checkerboard: Image by lazy { makeCheckerboardImage(80, 80, Color(0xFFA0A0A0u), Color(0xFF404040u), 8) }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        for (i in 0 until 4) {
            val image = if (i and 0x01 == 1) checkerboard else bitmap
            val cropRect = Rect(
                (i * 12).toFloat(), (i * 8).toFloat(),
                (image.width - i * 8).toFloat(), (image.height - i * 12).toFloat(),
            )
            val dx = (i * 5).toFloat()
            val dy = (i * 10).toFloat()
            val paint = Paint(imageFilter = ImageFilter.Offset(dx, dy))
            drawClippedImage(canvas, image, paint, 1f, cropRect)
            canvas.translate((image.width + 12).toFloat(), 0f)
        }

        val cropRect = Rect(0f, 0f, 100f, 100f)
        val paint = Paint(imageFilter = ImageFilter.Offset(-5f, -10f))
        drawClippedImage(canvas, bitmap, paint, 2f, cropRect)
    }

    private fun drawClippedImage(
        canvas: GmCanvas,
        image: Image,
        paint: Paint,
        scale: Float,
        cropRect: Rect,
    ) {
        val clipRect = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.scale(scale, scale)
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
        canvas.restore()

        val strokePaint = Paint(color = Color.RED, style = PaintStyle.STROKE, strokeWidth = 2f)
        canvas.drawRect(cropRect, strokePaint)
    }

    private fun makeStringImage(w: Int, h: Int, color: Color, str: String): Image {
        val surf = org.graphiks.kanvas.surface.Surface(w, h)
        surf.canvas {
            val font = Font(typeface, size = 96f)
            val paint = Paint(color = color)
            drawString(str, 15f, 65f, font, paint)
        }
        return surf.makeImageSnapshot()
    }

    private fun makeCheckerboardImage(w: Int, h: Int, c1: Color, c2: Color, size: Int): Image {
        val surf = org.graphiks.kanvas.surface.Surface(w, h)
        surf.canvas {
            val paint = Paint(color = c2)
            var y = 0
            while (y < h) {
                var x = (y / size) % 2 * size
                while (x < w) {
                    drawRect(Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()), paint)
                    x += 2 * size
                }
                y += size
            }
        }
        return surf.makeImageSnapshot()
    }
}
