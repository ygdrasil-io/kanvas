package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class Xfermodes2Gm : SkiaGm {
    override val name = "xfermodes2"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 455
    override val height = 475

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 20f)

        val w = kSize.toFloat()
        val h = kSize.toFloat()
        val kRowW = 6

        var x = 0f
        var y = 0f
        val modes = BlendMode.entries
        for ((m, mode) in modes.withIndex()) {
            val r = Rect.fromLTRB(x, y, x + w, y + h)

            // Background
            val dark = Color.fromRGBA(0x42 / 255f, 0x41 / 255f, 0x42 / 255f, 1f)
            val light = Color.fromRGBA(0xD6 / 255f, 0xD3 / 255f, 0xD6 / 255f, 1f)
            val bgColor = if ((m / 6 + m) % 2 == 0) dark else light
            canvas.drawRect(r, Paint(color = bgColor))

            // Draw source blend
            canvas.save()
            val dstPaint = Paint(
                color = Color.fromRGBA(0f, 1f, 0.5f, 0.6f),
            )
            val srcPaint = Paint(
                color = Color.fromRGBA(0.5f, 0f, 0.8f, 0.6f),
                blendMode = mode,
            )
            canvas.drawRect(r, dstPaint)
            canvas.drawRect(r, srcPaint)
            canvas.restore()

            // Stroke frame
            val frame = Rect.fromLTRB(
                r.left - 0.5f, r.top - 0.5f,
                r.right + 0.5f, r.bottom + 0.5f,
            )
            canvas.drawRect(frame, Paint(style = PaintStyle.STROKE))

            x += w + 10f
            if ((m % kRowW) == kRowW - 1) {
                x = 0f
                y += h + 30f
            }
        }
    }

    private companion object {
        const val kSize: Int = 64
    }
}
