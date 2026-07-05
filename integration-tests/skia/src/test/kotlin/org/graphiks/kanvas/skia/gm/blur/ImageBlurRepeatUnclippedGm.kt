package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imageblurrepeatmode.cpp::DEF_SIMPLE_GM(imageblurrepeatunclipped, …)`.
 * Demonstrates correct repeat-blur behaviour when the canvas is not clipped.
 * @see https://github.com/google/skia/blob/main/gm/imageblurrepeatmode.cpp
 */
class ImageBlurRepeatUnclippedGm : SkiaGm {
    override val name = "imageblurrepeatunclipped"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas, 256, 128, 8)

        val bmpW = 100
        val bmpH = 20
        val pixels = ByteArray(bmpW * bmpH * 4)
        for (y in 0 until bmpH) {
            for (x in 0 until bmpW) {
                val i = (y * bmpW + x) * 4
                if (y < 10) {
                    pixels[i] = (-1).toByte()
                    pixels[i + 1] = 0
                    pixels[i + 2] = 0
                } else {
                    pixels[i] = 0
                    pixels[i + 1] = 0
                    pixels[i + 2] = (-1).toByte()
                }
                pixels[i + 3] = (-1).toByte()
            }
        }
        val img = Image.fromPixels(bmpW, bmpH, pixels, ColorType.RGBA_8888, "imageblurrepeatunclipped")

        val filter = ImageFilter.Blur(0f, 10f, TileMode.REPEAT)
        val paint = Paint(imageFilter = filter)

        canvas.translate(0f, 50f)

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(0f, 0f, bmpW.toFloat(), (bmpH + 30).toFloat()))
        canvas.drawImage(img, Rect.fromXYWH(0f, 0f, bmpW.toFloat(), bmpH.toFloat()), paint)
        canvas.restore()

        canvas.translate(110f, 0f)
        canvas.save()
        canvas.clipRect(Rect.fromXYWH(0f, -30f, bmpW.toFloat(), 10f))
        canvas.drawImage(img, Rect.fromXYWH(0f, 0f, bmpW.toFloat(), bmpH.toFloat()), paint)
        canvas.restore()

        val line = Paint(color = Color.BLACK, style = PaintStyle.STROKE)
        canvas.drawRect(Rect.fromXYWH(0f, -30f, 99f, 9f), line)
    }

    private fun drawCheckerboard(canvas: GmCanvas, w: Int, h: Int, checkSize: Int) {
        val c1 = Color.fromRGBA(0.753f, 0.753f, 0.753f, 1f)
        val c2 = Color.fromRGBA(0.502f, 0.502f, 0.502f, 1f)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, w.toFloat(), h.toFloat()), Paint(color = c1))
        var y = 0
        while (y < h) {
            var x = (y / checkSize) % 2 * checkSize
            while (x < w) {
                canvas.drawRect(
                    Rect.fromXYWH(x.toFloat(), y.toFloat(), checkSize.toFloat(), checkSize.toFloat()),
                    Paint(color = c2),
                )
                x += 2 * checkSize
            }
            y += checkSize
        }
    }
}
