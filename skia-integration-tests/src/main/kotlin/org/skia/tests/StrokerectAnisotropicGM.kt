package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/strokerect_anisotropic.cpp::StrokeRectAnisotropicGM`
 * (160 × 160).
 *
 * Wrings out anisotropic stroke-rect bugs (repro for crbug.com/935303).
 * 4×2 grid : `{miter, miter-half-pixel, bevel, bevel-half-pixel}` ×
 * `{AA, non-AA}`, each cell drawing a `1000×20` rect under `scale(0.03,
 * 2)` (so the on-screen rect is `30×40`).
 *
 * The "half-pixel" columns shift the translate by 0.5 px pre-scale to
 * land the stroke edges between integer pixel rows. AA columns should
 * blend smoothly across the half-pixel offset; non-AA should snap.
 */
public class StrokerectAnisotropicGM : GM() {

    override fun getName(): String = "strokerect_anisotropic"
    override fun getISize(): SkISize = SkISize.Make(160, 160)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val aaPaint = SkPaint().apply {
            color = SkColorSetARGB(255, 0, 0, 0)
            isAntiAlias = true
            strokeWidth = 10f
            style = SkPaint.Style.kStroke_Style
        }
        val bwPaint = SkPaint().apply {
            color = SkColorSetARGB(255, 0, 0, 0)
            strokeWidth = 10f
            style = SkPaint.Style.kStroke_Style
        }

        // Two miter columns.
        drawSqooshedRect(c, 20f, 40.5f, aaPaint)
        drawSqooshedRect(c, 20f, 110.5f, bwPaint)

        drawSqooshedRect(c, 60.5f, 40f, aaPaint)
        drawSqooshedRect(c, 60.5f, 110f, bwPaint)

        aaPaint.strokeJoin = SkPaint.Join.kBevel_Join
        bwPaint.strokeJoin = SkPaint.Join.kBevel_Join

        // Two bevel columns.
        drawSqooshedRect(c, 100f, 40.5f, aaPaint)
        drawSqooshedRect(c, 100f, 110.5f, bwPaint)

        drawSqooshedRect(c, 140.5f, 40f, aaPaint)
        drawSqooshedRect(c, 140.5f, 110f, bwPaint)
    }

    private fun drawSqooshedRect(canvas: SkCanvas, tx: Float, ty: Float, p: SkPaint) {
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(0.03f, 2f)
        // Substitute drawRect → drawPath : our drawRect fast path
        // strokes uniformly in device space, ignoring the CTM's
        // anisotropic scale ; routing through drawPath uses the source-
        // space stroker so the 10-px stroke becomes 0.3 wide × 20 tall
        // after the (0.03, 2) scale, matching upstream.
        canvas.drawPath(SkPath.Rect(SkRect.MakeLTRB(-500f, -10f, 500f, 10f)), p)
        canvas.restore()
    }
}
