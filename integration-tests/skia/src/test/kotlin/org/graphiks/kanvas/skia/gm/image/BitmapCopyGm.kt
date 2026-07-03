package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmapcopy.cpp::BitmapCopyGM`.
 * Tests Bitmap copy operations across different color types.
 * @see https://github.com/google/skia/blob/main/gm/bitmapcopy.cpp
 */
class BitmapCopyGm : SkiaGm {
    override val name = "bitmapcopy"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 540
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSrcBitmap()
        val dstBms = COLOR_TYPES.map { ct -> copyTo(src, ct) }

        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)),
        )

        val font = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = 12f,
        )
        val paint = Paint(antiAlias = true)

        val horizMargin = 10f
        val vertMargin = 10f
        val cellW = 60f
        val cellH = 50f
        val horizOffset = cellW + horizMargin
        val vertOffset = cellH + vertMargin

        canvas.translate(20f, 20f)

        for (i in COLOR_TYPE_LABELS.indices) {
            canvas.save()
            val name = COLOR_TYPE_LABELS[i]
            val tw = font.measureText(name)
            val x = (cellW - tw) / 2f
            canvas.drawString(name, x, 14f, font, paint)

            canvas.translate(0f, vertOffset)
            val tx = (cellW - 40f) / 2f
            val img = dstBms[i].toImage()
            canvas.drawImage(img, Rect(tx, 0f, tx + 40f, 40f))
            canvas.restore()

            canvas.translate(horizOffset, 0f)
        }
    }

    private fun makeSrcBitmap(): Bitmap {
        val bm = Bitmap(40, 40)
        val w2 = 20; val h2 = 20
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                val YELLOW = Color.fromRGBA(1f, 1f, 0f)
                val color = when {
                    x < w2 && y < h2 -> Color.RED
                    x >= w2 && y < h2 -> Color.GREEN
                    x < w2 && y >= h2 -> Color.BLUE
                    else -> YELLOW
                }
                bm.setPixel(x, y, color)
            }
        }
        return bm
    }

    private fun copyTo(src: Bitmap, ct: ColorType): Bitmap {
        val dst = Bitmap(src.width, src.height, ct)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                dst.setPixel(x, y, src.getPixel(x, y))
            }
        }
        return dst
    }

    private companion object {
        private val COLOR_TYPES = listOf(
            ColorType.RGB_565,
            ColorType.ARGB_4444,
            ColorType.RGBA_8888,
        )
        private val COLOR_TYPE_LABELS = listOf("565", "4444", "8888")
    }
}
