package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/dashing.cpp::DashingGM` (640 × 340).
 *
 * Phase 7b validation GM — exercises [SkDashPathEffect] on a grid of
 * dashed `drawLine` calls under varying stroke widths, intervals,
 * and AA modes. The line-only nature of this GM means every code
 * path through the pathEffect → stroker → fill pipeline is a
 * straight-segment dash, the simplest case (no curve flattening).
 *
 * Layout (12-row main grid + 4 trailing rows of edge cases) :
 *
 *  - **Main grid** : 3 stroke widths (`{0, 1, 8}` from `width³` for
 *    `width ∈ 0..2`) × 2 patterns (`{1:1, 4:1}` ratio) × 2 AA modes
 *    (off, on) = 12 rows. Pattern intervals scale with the stroke
 *    width so dashes stay visually proportional.
 *  - **Giant dash** : 20 000-unit line with `1:1` pattern — exercises
 *    a long-arc-length dash (Skia regression for skipping nearly-zero
 *    delta-T).
 *  - **Zero-length dashes** : `2:2` and `4:4` patterns with `finalX = 0`
 *    (degenerate line — no draw).
 *  - **0:0 dash** : both intervals zero — pathEffect produces an empty
 *    path, nothing is drawn.
 */
public class DashingGM : GM() {

    override fun getName(): String = "dashing"
    override fun getISize(): SkISize = SkISize.Make(640, 340)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }

        c.translate(20f, 20f)
        c.translate(0f, 0.5f)

        // Main grid : 3 widths × 2 patterns × 2 AA modes.
        for (width in 0..2) {
            val w = width * width * width   // 0, 1, 8
            for (pattern in arrayOf(intArrayOf(1, 1), intArrayOf(4, 1))) {
                for (aa in 0..1) {
                    paint.isAntiAlias = aa != 0
                    paint.strokeWidth = w.toFloat()
                    val scale = if (w > 0) w else 1
                    drawLine(c, pattern[0] * scale, pattern[1] * scale, paint)
                    c.translate(0f, 20f)
                }
            }
        }

        showGiantDash(c)
        c.translate(0f, 20f)

        showZeroLenDash(c)
        c.translate(0f, 20f)

        // Final row : 0 on, 0 off dashed line — pathEffect produces an
        // empty path, nothing draws.
        paint.strokeWidth = 8f
        drawLine(c, 0, 0, paint)
    }

    /**
     * Draw a horizontal dashed line of length [finalX] with `[on, off]`
     * intervals. Mirrors upstream's `drawline` helper.
     */
    private fun drawLine(
        canvas: SkCanvas,
        on: Int, off: Int,
        paint: SkPaint,
        finalX: Float = 600f,
        finalY: Float = 0f,
        phase: Float = 0f,
        startX: Float = 0f,
        startY: Float = 0f,
    ) {
        val p = paint.copy()
        p.pathEffect = SkDashPathEffect.Make(floatArrayOf(on.toFloat(), off.toFloat()), phase)
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

    /** 20 000-unit line with `1:1` pattern. */
    private fun showGiantDash(canvas: SkCanvas) {
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        drawLine(canvas, 1, 1, paint, finalX = 20_000f)
    }

    /** Two zero-finalX (degenerate) dashed lines — neither draws anything. */
    private fun showZeroLenDash(canvas: SkCanvas) {
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        drawLine(canvas, 2, 2, paint, finalX = 0f)
        paint.strokeWidth = 2f
        canvas.translate(0f, 20f)
        drawLine(canvas, 4, 4, paint, finalX = 0f)
    }
}
