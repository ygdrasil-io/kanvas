package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class XfermodeImageFilterGm : SkiaGm {
    override val name = "xfermodeimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = WIDTH
    override val height = HEIGHT

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stringImage = makeStringImage(80, 80, 0xD000D0, 15, 65, 96, "e")
        val checkerboard = makeCheckerboardImage(80, 80, 0xA0A0A0, 0x404040, 8)

        canvas.drawColor(0f, 0f, 0f, 1f)

        val modes = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
            BlendMode.PLUS, BlendMode.MODULATE, BlendMode.SCREEN, BlendMode.OVERLAY,
            BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN,
            BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION,
            BlendMode.MULTIPLY, BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR,
            BlendMode.LUMINOSITY,
        )

        var x = 0f
        var y = 0f
        val cellW = stringImage.width.toFloat()
        val cellH = stringImage.height.toFloat()

        for (mode in modes) {
            drawBlendCell(canvas, stringImage, checkerboard, mode, x, y)
            x += cellW + MARGIN
            if (x + cellW > WIDTH) {
                x = 0f
                y += cellH + MARGIN
            }
        }

        val clipRect = Rect(0f, 0f, cellW + 4f, cellH + 4f)

        run {
            val surface = Surface(stringImage.width, stringImage.height)
            surface.canvas {
                drawColor(Color.fromRGBA(1f, 1f, 1f, 1f))
                val checkPaint = Paint(color = Color.fromRGBA(0.63f, 0.63f, 0.63f, 1f))
                drawRect(Rect(0f, 0f, cellW, cellH), checkPaint)
            }
            val checkImage = surface.makeImageSnapshot()
            canvas.save()
            canvas.translate(x, y)
            canvas.clipRect(clipRect)
            canvas.drawImage(stringImage, Rect(0f, 0f, cellW, cellH), Paint())
            canvas.restore()
        }
    }

    private fun drawBlendCell(canvas: GmCanvas, fgImage: Image, bgImage: Image, mode: BlendMode, x: Float, y: Float) {
        val cellW = fgImage.width.toFloat()
        val cellH = fgImage.height.toFloat()
        val r = Rect(x, y, x + cellW, y + cellH)
        val cellRect = Rect(0f, 0f, cellW, cellH)

        canvas.save()
        canvas.translate(x, y)
        canvas.clipRect(cellRect)

        canvas.saveLayer(cellRect, Paint(blendMode = mode))
        canvas.drawImage(bgImage, cellRect)
        canvas.drawImage(fgImage, cellRect)
        canvas.restore()

        canvas.restore()
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
        const val WIDTH: Int = 600
        const val HEIGHT: Int = 700
        const val MARGIN: Float = 12f
    }
}
