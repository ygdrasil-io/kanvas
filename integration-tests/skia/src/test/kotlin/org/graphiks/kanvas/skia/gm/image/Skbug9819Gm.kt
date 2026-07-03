package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/skbug_9819.cpp :: skbug_9819`.
 *
 * Allocates 1×1 RGBA and BGRA bitmaps, fills both with opaque yellow,
 * and scales each up to 128×128. If BGRA round-trips correctly through
 * [Bitmap.getPixel] → [Bitmap.toImage] → drawImage, both squares render
 * as solid yellow (the original bug drew one in cyan due to channel-order
 * confusion). MarkGMGood markers are emitted at the centre of each square.
 * @see https://github.com/google/skia/blob/main/gm/skbug_9819.cpp
 */
class Skbug9819Gm : SkiaGm {
    override val name = "skbug_9819"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val yellow = Color.fromRGBA(1f, 1f, 0f, 1f)

        val rgba = Bitmap(1, 1, ColorType.RGBA_8888)
        rgba.eraseColor(yellow)

        val bgra = Bitmap(1, 1, ColorType.BGRA_8888)
        bgra.eraseColor(yellow)

        canvas.save()
        canvas.scale(128f, 128f)
        canvas.drawImage(rgba.toImage(), Rect(0f, 0f, 1f, 1f))
        canvas.drawImage(bgra.toImage(), Rect(0f, 1f, 1f, 2f))
        canvas.restore()

        markGmGood(canvas, 192f, 64f)
        markGmGood(canvas, 192f, 192f)
    }

    private fun markGmGood(canvas: GmCanvas, x: Float, y: Float) {
        val translucent = Paint(color = Color.BLACK, blendMode = BlendMode.SRC_OVER).let {
            Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.314f), blendMode = BlendMode.SRC_OVER)
        }
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
