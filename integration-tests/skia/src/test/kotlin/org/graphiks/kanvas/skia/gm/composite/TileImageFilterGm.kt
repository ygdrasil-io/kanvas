package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class TileImageFilterGm : SkiaGm {
    override val name = "tileimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = WIDTH
    override val height = HEIGHT

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stringImage = makeStringImage(50, 50, 0xD000D0, 10, 45, 50, "e")
        val checkerboard = makeCheckerboardImage(80, 80, 0xA0A0A0, 0x404040, 8)

        canvas.drawColor(0f, 0f, 0f, 1f)

        val red = Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f), style = PaintStyle.STROKE)
        val blue = Paint(color = Color.fromRGBA(0f, 0f, 1f, 1f), style = PaintStyle.STROKE)

        var x = 0f
        var y = 0f

        for (i in 0 until 4) {
            val image: Image = if ((i and 0x01) != 0) checkerboard else stringImage
            val srcRect = Rect(
                (image.width / 4).toFloat(),
                (image.height / 4).toFloat(),
                (image.width / 4 + image.width / (i + 1)).toFloat(),
                (image.height / 4 + image.height / (i + 1)).toFloat(),
            )
            val dstRect = Rect(
                (i * 8).toFloat(),
                (i * 4).toFloat(),
                (i * 8 + image.width - i * 12).toFloat(),
                (i * 4 + image.height - i * 12).toFloat(),
            )

            val tileFilter = ImageFilter.Tile(srcRect, dstRect, null)
            canvas.save()
            canvas.translate(x, y)
            canvas.saveLayer(Rect(x, y, x + image.width, y + image.height), Paint(imageFilter = tileFilter))
            canvas.drawImage(image, Rect(x, y, x + image.width.toFloat(), y + image.height.toFloat()))
            canvas.restore()

            canvas.drawRect(Rect(x + srcRect.left, y + srcRect.top, x + srcRect.right, y + srcRect.bottom), red)
            canvas.drawRect(Rect(x + dstRect.left, y + dstRect.top, x + dstRect.right, y + dstRect.bottom), blue)
            canvas.restore()

            x += image.width + MARGIN
            if (x + image.width > WIDTH) {
                x = 0f
                y += image.height + MARGIN
            }
        }

        run {
            val srcRect = Rect(0f, 0f, stringImage.width.toFloat(), stringImage.height.toFloat())
            val dstRect = Rect(0f, 0f, (stringImage.width * 2).toFloat(), (stringImage.height * 2).toFloat())
            val tile = ImageFilter.Tile(srcRect, dstRect, null)

            canvas.save()
            canvas.translate(x, y)
            canvas.clipRect(Rect(x, y, x + dstRect.width, y + dstRect.height))
            canvas.saveLayer(Rect(x, y, x + dstRect.width, y + dstRect.height), Paint(imageFilter = tile))
            canvas.drawImage(stringImage, Rect(x, y, x + stringImage.width.toFloat(), y + stringImage.height.toFloat()))
            canvas.restore()
            canvas.drawRect(Rect(x + srcRect.left, y + srcRect.top, x + srcRect.right, y + srcRect.bottom), red)
            canvas.drawRect(Rect(x + dstRect.left, y + dstRect.top, x + dstRect.right, y + dstRect.bottom), blue)
            canvas.restore()
        }

        run {
            val srcRect = Rect(0f, 0f, 50f, 50f)
            val dstRect = Rect(0f, 0f, 100f, 100f)

            canvas.save()
            canvas.translate(0f, 100f)
            val greenCF = ColorFilter.Blend(Color.fromRGBA(0f, 1f, 0f, 1f), org.graphiks.kanvas.paint.BlendMode.SRC)
            val coloured = ImageFilter.ColorFilter(greenCF, null)
            val tileFilter = ImageFilter.Tile(srcRect, dstRect, coloured)
            val tilePaint = Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f), imageFilter = tileFilter)
            canvas.clipRect(Rect(0f, 0f, dstRect.width, dstRect.height))
            canvas.saveLayer(dstRect, tilePaint)
            canvas.restore()
            canvas.restore()
        }
    }

    private fun makeStringImage(w: Int, h: Int, color: Int, x: Int, y: Int, textSize: Int, str: String): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val paint = Paint(color = Color.fromRGBA(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                ((color shr 24) and 0xFF) / 255f,
            ))
            val font = Font(typeface, size = textSize.toFloat())
            drawString(str, x.toFloat(), y.toFloat(), font, paint)
        }
        return surface.makeImageSnapshot()
    }

    private fun makeCheckerboardImage(w: Int, h: Int, c1: Int, c2: Int, size: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val bgColor = Color.fromRGBA(
                ((c1 shr 16) and 0xFF) / 255f,
                ((c1 shr 8) and 0xFF) / 255f,
                (c1 and 0xFF) / 255f,
                ((c1 shr 24) and 0xFF) / 255f,
            )
            drawColor(bgColor)
            val fgColor = Color.fromRGBA(
                ((c2 shr 16) and 0xFF) / 255f,
                ((c2 shr 8) and 0xFF) / 255f,
                (c2 and 0xFF) / 255f,
                ((c2 shr 24) and 0xFF) / 255f,
            )
            val paint = Paint(color = fgColor)
            var yy = 0
            while (yy < h) {
                var xx = (yy / size) % 2 * size
                while (xx < w) {
                    drawRect(
                        Rect(xx.toFloat(), yy.toFloat(), (xx + size).toFloat(), (yy + size).toFloat()),
                        paint,
                    )
                    xx += 2 * size
                }
                yy += size
            }
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        const val WIDTH: Int = 400
        const val HEIGHT: Int = 200
        const val MARGIN: Float = 12f
    }
}
