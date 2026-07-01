package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class AAClipGm : SkiaGm {
    override val name = "aaclip"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 240
    override val height = 120

    private fun draw(canvas: GmCanvas, target: Rect, x: Int, y: Int) {
        val borderPaint = Paint(color = Color.fromRGBA(0f, 0xDD / 255f, 0f, 1f), antiAlias = true)
        val backgroundPaint = Paint(color = Color.fromRGBA(0xDD / 255f, 0f, 0f, 1f), antiAlias = true)
        val foregroundPaint = Paint(color = Color.fromRGBA(0f, 0f, 0xDD / 255f, 1f), antiAlias = true)

        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        var t = Rect.fromLTRB(
            target.left - 2f, target.top - 2f,
            target.right + 2f, target.bottom + 2f,
        )
        canvas.drawRect(t, borderPaint)
        canvas.drawRect(target, backgroundPaint)
        canvas.clipRect(target)
        t = Rect.fromLTRB(
            target.left - 4f, target.top - 4f,
            target.right + 4f, target.bottom + 4f,
        )
        canvas.drawRect(t, foregroundPaint)
        canvas.restore()
    }

    private fun drawSquare(canvas: GmCanvas, x: Int, y: Int) =
        draw(canvas, Rect.fromLTRB(0f, 0f, 10f, 10f), x, y)

    private fun drawColumn(canvas: GmCanvas, x: Int, y: Int) =
        draw(canvas, Rect.fromLTRB(0f, 0f, 1f, 10f), x, y)

    private fun drawBar(canvas: GmCanvas, x: Int, y: Int) =
        draw(canvas, Rect.fromLTRB(0f, 0f, 10f, 1f), x, y)

    private fun drawRectTests(canvas: GmCanvas) {
        drawSquare(canvas, 10, 10)
        drawColumn(canvas, 30, 10)
        drawBar(canvas, 10, 30)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawRectTests(canvas)
        for (i in 0 until 4) {
            canvas.translate(1f / 5f, 1f / 5f)
            canvas.translate(50f, 0f)
            drawRectTests(canvas)
        }
    }
}
