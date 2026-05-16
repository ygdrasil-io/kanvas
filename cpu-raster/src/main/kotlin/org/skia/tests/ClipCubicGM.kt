package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/aaclip.cpp:ClipCubicGM` (skbug.com/40034846).
 *
 * Two pairs of "vertical cubic curve" + "horizontal cubic curve":
 *  - The horizontal variant is built by 90°-rotating the vertical one around
 *    `(W/2, H/2)` via [SkPath.makeTransform], exercising the new
 *    rotation-around-pivot path of [SkMatrix.MakeRotate].
 *  - Each pair is drawn twice — once unclipped and once with a `clipRect`
 *    that crops the middle half — so the rasterizer's clip-edge arithmetic
 *    is visible on a curve.
 *
 * Reference image: `clipcubic.png`, 400 × 410, default white BG. Background
 * rect is 565-quantised purple `0xFF8888FF`.
 */
public class ClipCubicGM : GM() {

    private val W: SkScalar = 100f
    private val H: SkScalar = 240f

    private lateinit var fVPath: SkPath
    private lateinit var fHPath: SkPath

    override fun getName(): String = "clipcubic"
    override fun getISize(): SkISize = SkISize.Make(400, 410)

    override fun onOnceBeforeDraw() {
        fVPath = SkPathBuilder()
            .moveTo(W, 0f)
            .cubicTo(W, H - 10f, 0f, 10f, 0f, H)
            .detach()
        // Rotate 90° around the path's centre to get the horizontal variant.
        val pivot = SkMatrix.MakeRotate(90f, W / 2f, H / 2f)
        fHPath = fVPath.makeTransform(pivot)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(80f, 10f)
        drawAndClip(c, fVPath, 200f, 0f)
        c.translate(0f, 200f)
        drawAndClip(c, fHPath, 200f, 0f)
    }

    private fun drawAndClip(c: SkCanvas, p: SkPath, dx: SkScalar, dy: SkScalar) {
        // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);`.
        c.withSave {
            val r = SkRect.MakeXYWH(0f, H / 4f, W, H / 2f)
            val bgPaint = SkPaint().apply {
                color = ToolUtils.colorTo565(0xFF8888FF.toInt())
            }

            // Unclipped: just the curve over the bg rect.
            drawRect(r, bgPaint)
            doDraw(this, p)

            translate(dx, dy)

            // Clipped: re-draw the bg rect and curve, then clip to r and re-draw.
            drawRect(r, bgPaint)
            clipRect(r)
            doDraw(this, p)
        }
    }

    private fun doDraw(c: SkCanvas, p: SkPath) {
        val paint = SkPaint().apply { isAntiAlias = true }
        // Fill in light gray.
        paint.color = 0xFFCCCCCC.toInt()
        c.drawPath(p, paint)
        // Stroke outline in red.
        paint.color = 0xFFFF0000.toInt()
        paint.style = SkPaint.Style.kStroke_Style
        c.drawPath(p, paint)
    }
}
