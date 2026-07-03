package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/unpremul.cpp :: unpremul`.
 *
 * Exercises the unpremul codepath in two ways:
 *
 *  1. Top half — draw a translucent slate-red rect (`0xBF400000`)
 *     with [BlendMode.SRC].
 *  2. Bottom half — allocate a 100×100 bitmap, eraseColor it with
 *     the same colour, and drawImage with [BlendMode.SRC].
 *
 * Both halves emit a MarkGMGood marker (green check).
 * @see https://github.com/google/skia/blob/main/gm/unpremul.cpp
 */
class UnpremulGm : SkiaGm {
    override val name = "unpremul"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val tolerance = 8
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val color = Color.fromRGBA(0x40 / 255f, 0f, 0f, 0xBF / 255f)

        canvas.save()
        val paint = Paint(color = color, blendMode = BlendMode.SRC)
        canvas.drawRect(Rect(0f, 0f, 100f, 100f), paint)
        markGmGood(canvas, 140f, 40f)
        canvas.restore()

        canvas.translate(0f, 100f)

        val bm = Bitmap(100, 100)
        bm.eraseColor(color)
        canvas.drawImage(
            bm.toImage(), Rect(0f, 0f, 100f, 100f),
            Paint(blendMode = BlendMode.SRC),
        )
        markGmGood(canvas, 140f, 40f)
    }

    private fun markGmGood(canvas: GmCanvas, x: Float, y: Float) {
        val translucent = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.314f))
        canvas.saveLayer(null, translucent)
        canvas.translate(x, y)
        canvas.scale(2f, 2f)

        val greenFill = Paint.fill(Color.fromRGBA(27f / 255f, 158f / 255f, 119f / 255f))
        canvas.drawCircle(0f, 0f, 12f, greenFill)

        val checkmark = Paint(
            color = Color.TRANSPARENT,
            blendMode = BlendMode.SRC,
            strokeWidth = 2f,
            style = PaintStyle.STROKE,
        )
        canvas.drawLine(-6f, 0f, -1f, 5f, checkmark)
        canvas.drawLine(-1f, 5f, 7f, -5f, checkmark)

        canvas.restore()
    }
}
