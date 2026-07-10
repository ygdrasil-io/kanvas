package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmapfilters.cpp::FilterGM`.
 * @see https://github.com/google/skia/blob/main/gm/bitmapfilters.cpp
 */
class BitmapFiltersGm : SkiaGm {
    override val name = "bitmapfilters"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 540
    override val height = 250

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)

        val pixels = ByteArray(2 * 2 * 4).apply {
            // (0,0) = RED, (1,0) = GREEN
            this[0] = (-1).toByte(); this[1] = 0; this[2] = 0; this[3] = (-1).toByte()
            this[4] = 0; this[5] = (-1).toByte(); this[6] = 0; this[7] = (-1).toByte()
            // (0,1) = BLUE, (1,1) = WHITE
            this[8] = 0; this[9] = 0; this[10] = (-1).toByte(); this[11] = (-1).toByte()
            this[12] = (-1).toByte(); this[13] = (-1).toByte(); this[14] = (-1).toByte(); this[15] = (-1).toByte()
        }
        val img = Image.fromPixels(2, 2, pixels, ColorType.RGBA_8888, "bitmapfilters")
        val scale = 32

        canvas.translate(10f, 10f)
        val rowHeight = drawRow(canvas, img, "ARGB_4444", scale)
        canvas.translate(0f, rowHeight)
        drawRow(canvas, img, "RGB_565", scale)
        canvas.translate(0f, rowHeight)
        drawRow(canvas, img, "RGBA_8888", scale)
    }

    private fun drawRow(canvas: GmCanvas, img: Image, colorTypeName: String, scale: Int): Float {
        canvas.save()
        canvas.drawString(colorTypeName, 0f, (img.height * scale * 5 / 8).toFloat(), font, Paint())

        canvas.translate(48f, 0f)
        val dst = Rect.fromXYWH(0f, 0f, (img.width * scale).toFloat(), (img.height * scale).toFloat())
        canvas.drawImage(img, dst)

        val dst2 = Rect.fromXYWH(dst.right + dst.width / 4f, 0f, dst.width, dst.height)
        val p2 = Paint()
        canvas.drawImage(img, dst2, p2)

        canvas.restore()
        return (dst.width * 2f / 3f)
    }
}
