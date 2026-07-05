package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.min

/**
 * Port of Skia's `gm/repeated_bitmap.cpp::repeated_bitmap_jpg`.
 * Identical to [RepeatedBitmapGm] but uses `images/color_wheel.jpg`.
 * @see https://github.com/google/skia/blob/main/gm/repeated_bitmap.cpp
 */
class RepeatedBitmapJpgGm : SkiaGm {
    override val name = "repeated_bitmap_jpg"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 576
    override val height = 576

    private var image: Image? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/color_wheel.jpg")?.readBytes()
        if (bytes != null) {
            image = Image.decode(bytes)
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas, Color.fromRGBA(156f / 255f, 154f / 255f, 156f / 255f, 1f), Color.WHITE, 12)
        val img = image ?: return

        val bgPaint = Paint(color = Color.fromRGBA(49f / 255f, 48f / 255f, 49f / 255f, 1f))
        val bgRect = Rect.fromLTRB(-68f, -68f, 68f, 68f)
        val scale = min(128f / img.width, 128f / img.height)

        for (j in 0 until 4) {
            for (i in 0 until 4) {
                canvas.save()
                canvas.translate(96f + 192f * i, 96f + 192f * j)
                canvas.rotate(18f * (i + 4 * j))
                canvas.drawRect(bgRect, bgPaint)
                canvas.scale(scale, scale)
                val dst = Rect.fromXYWH(-img.width / 2f, -img.height / 2f, img.width.toFloat(), img.height.toFloat())
                canvas.drawImage(img, dst)
                canvas.restore()
            }
        }
    }

    private fun drawCheckerboard(canvas: GmCanvas, color1: Color, color2: Color, size: Int) {
        for (y in 0 until 576 step size) {
            for (x in 0 until 576 step size) {
                val isEven = (x / size + y / size) % 2 == 0
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()), Paint(color = if (isEven) color1 else color2))
            }
        }
    }
}
