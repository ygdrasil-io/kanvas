package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/aaclip.cpp:ClipCubicGM` (skbug.com/40034846).
 *
 * Two pairs of "vertical cubic curve" + "horizontal cubic curve":
 *  - The horizontal variant is built by 90°-rotating the vertical one around
 *    `(W/2, H/2)` via [Path.transform], exercising the new
 *    rotation-around-pivot path of [Matrix33].
 *  - Each pair is drawn twice — once unclipped and once with a `clipRect`
 *    that crops the middle half — so the rasterizer's clip-edge arithmetic
 *    is visible on a curve.
 *
 * Reference image: `clipcubic.png`, 400 × 410, default white BG. Background
 * rect is 565-quantised purple `0xFF8888FF`.
 * @see https://github.com/google/skia/blob/main/gm/aaclip.cpp
 */
class ClipCubicGm : SkiaGm {
    override val name = "clipcubic"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 410

    private val W = 100f
    private val H = 240f

    private val vPath: Path by lazy {
        Path {
            moveTo(W, 0f)
            cubicTo(W, H - 10f, 0f, 10f, 0f, H)
        }
    }

    private val hPath: Path by lazy {
        val pivot = Matrix33.translate(W / 2f, H / 2f) *
            Matrix33.rotate(90f) *
            Matrix33.translate(-W / 2f, -H / 2f)
        vPath.transform(pivot)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(80f, 10f)
        drawAndClip(canvas, vPath, 200f, 0f)
        canvas.translate(0f, 200f)
        drawAndClip(canvas, hPath, 200f, 0f)
    }

    private fun drawAndClip(canvas: GmCanvas, p: Path, dx: Float, dy: Float) {
        canvas.save()
        val r = Rect.fromXYWH(0f, H / 4f, W, H / 2f)
        val bgPaint = Paint(color = Color(0xFF8888FFu))

        canvas.drawRect(r, bgPaint)
        doDraw(canvas, p)

        canvas.translate(dx, dy)

        canvas.drawRect(r, bgPaint)
        canvas.clipRect(r)
        doDraw(canvas, p)
        canvas.restore()
    }

    private fun doDraw(canvas: GmCanvas, p: Path) {
        var paint = Paint(antiAlias = true, color = Color(0xFFCCCCCCu))
        canvas.drawPath(p, paint)
        paint = paint.copy(color = Color.RED, style = PaintStyle.STROKE)
        canvas.drawPath(p, paint)
    }
}
