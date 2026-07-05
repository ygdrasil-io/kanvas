package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/skbug_8664.cpp`.
 * Regression for skbug.com/8664 — mipmap+scissor interference on Adreno 330.
 * Draws a scaled image with clip+rotate overlay.
 * @see https://github.com/google/skia/blob/main/gm/skbug_8664.cpp
 */
class Skbug8664Gm : SkiaGm {
    override val name = "skbug_8664"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 830
    override val height = 550

    private data class Xform(val sx: Float, val sy: Float, val tx: Float, val ty: Float)

    private val sourceImage: Image = run {
        val w = 256; val h = 256
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val r = (x * 255 / (w - 1)) and 0xFF
                val g = (y * 255 / (h - 1)) and 0xFF
                val b = ((x + y) * 255 / (w + h - 2)) and 0xFF
                pixels[i] = r.toByte(); pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "mandrill-standin")
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val xforms = listOf(
            Xform(1f, 1f, 0f, 0f),
            Xform(0.5f, 0.5f, 530f, 0f),
            Xform(0.25f, 0.25f, 530f, 275f),
            Xform(0.125f, 0.125f, 530f, 420f),
        )

        val image = sourceImage
        val overlayPaint = Paint(color = Color(0x80FFFFFFu))

        canvas.drawColor(136f / 255f, 136f / 255f, 136f / 255f, 1f)

        canvas.translate(20f, 20f)
        for (xform in xforms) {
            canvas.save()
            canvas.translate(xform.tx, xform.ty)
            canvas.scale(xform.sx, xform.sy)

            val imgRect = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
            canvas.drawImage(image, imgRect)

            val inner = Rect(32f, 32f, 480f, 480f)
            val outer = Rect(
                inner.left - 16f, inner.top - 16f,
                inner.right + 16f, inner.bottom + 16f,
            )

            canvas.save()
            canvas.clipRect(inner)
            canvas.rotate(20f)
            canvas.drawRect(outer, overlayPaint)
            canvas.restore()

            canvas.restore()
        }
    }
}
