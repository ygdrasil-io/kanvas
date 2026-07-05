package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class ImageFiltersTransformedOriginalGm : SkiaGm {
    override val name = "imagefilterstransformed"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 420
    override val height = 240

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val checkerboard = makeCheckerboardImage(64, 64)
        val gradientCircle = makeGradientCircle(64, 64)

        val gradient = ImageFilter.ColorFilter(
            org.graphiks.kanvas.paint.ColorFilter.Matrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
            null,
        )
        val checker = ImageFilter.ColorFilter(
            org.graphiks.kanvas.paint.ColorFilter.Matrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
            null,
        )

        val filters = arrayOf(
            ImageFilter.Blur(12f, 0f, input = null),
            ImageFilter.DropShadow(0f, 15f, 8f, 0f, Color.GREEN, null),
            ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.R, 12f, gradient, checker),
            ImageFilter.Dilate(2f, 2f, checker),
            ImageFilter.Erode(2f, 2f, checker),
        )

        val margin = 20f
        val size = 60f

        for (j in 0 until 3) {
            canvas.save()
            canvas.translate(margin, 0f)
            for (i in filters.indices) {
                val paint = Paint(
                    color = Color.WHITE,
                    imageFilter = filters[i],
                    antiAlias = true,
                )
                canvas.save()
                canvas.translate(size * 0.5f, size * 0.5f)
                canvas.scale(0.8f, 0.8f)
                when (j) {
                    1 -> canvas.rotate(45f)
                    2 -> canvas.skew(0.5f, 0.2f)
                }
                canvas.translate(-size * 0.5f, -size * 0.5f)
                canvas.drawOval(Rect(0f, size * 0.1f, size, size * 0.6f + size * 0.1f), paint)
                canvas.restore()
                canvas.translate(size + margin, 0f)
            }
            canvas.restore()
            canvas.translate(0f, size + margin)
        }
    }

    private fun makeGradientCircle(width: Int, height: Int): Image = Image.placeholder(width, height)

    private fun makeCheckerboardImage(w: Int, h: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val on = ((x / 8) + (y / 8)) % 2 == 0
                val c = if (on) 0xFFA0A0A0u.toInt() else 0xFF404040u.toInt()
                val i = (y * w + x) * 4
                pixels[i] = (c and 0xFF).toByte()
                pixels[i + 1] = ((c ushr 8) and 0xFF).toByte()
                pixels[i + 2] = ((c ushr 16) and 0xFF).toByte()
                pixels[i + 3] = ((c ushr 24) and 0xFF).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels)
    }
}
