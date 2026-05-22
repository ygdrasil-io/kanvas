package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/dashing.cpp::Dashing4GM` (640 × 1100).
 *
 * Sweeps the full Cartesian product
 *   `(width³ ∈ {0, 1, 8}) × ({(1,1), (4,2), (0,4)}) × (aa = {off,on}) ×
 *    (cap = {kRound, kSquare})`
 * — 36 rows of dashed lines stacked at `20 px` spacing. The `(0,4)`
 * interval is the zero-on-interval probe (should produce a line of
 * cap squares / circles).
 *
 * Trailing rows :
 *  - 5 AA / BW variants showing dash placement at small finalX
 *    (`20`, `56`, `584`, `600 + 30` diagonal, `8` off-only).
 *  - Two overlapping-circle blocks (kRound / kSquare caps).
 *  - Two zero-length lines at the end (kRound cap, full / 1-on-30-off
 *    pattern).
 */
public class Dashing4GM : GM() {

    override fun getName(): String = "dashing4"
    override fun getISize(): SkISize = SkISize.Make(640, 1100)

    private fun drawLine(
        canvas: SkCanvas,
        on: Int, off: Int,
        paint: SkPaint,
        finalX: Float = 600f, finalY: Float = 0f,
        phase: Float = 0f,
        startX: Float = 0f, startY: Float = 0f,
    ) {
        val p = paint.copy()
        p.pathEffect = SkDashPathEffect.Make(floatArrayOf(on.toFloat(), off.toFloat()), phase)
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }

        c.translate(20f, 20f)
        c.translate(0.5f, 0.5f)

        val intervals = arrayOf(intArrayOf(1, 1), intArrayOf(4, 2), intArrayOf(0, 4))
        for (width in 0..2) {
            for (data in intervals) {
                for (aa in 0..1) {
                    for (cap in arrayOf(SkPaint.Cap.kRound_Cap, SkPaint.Cap.kSquare_Cap)) {
                        val w = width * width * width
                        paint.isAntiAlias = aa != 0
                        paint.strokeWidth = w.toFloat()
                        paint.strokeCap = cap
                        val scale = if (w != 0) w else 1
                        drawLine(c, data[0] * scale, data[1] * scale, paint)
                        c.translate(0f, 20f)
                    }
                }
            }
        }

        for (aa in 0..1) {
            paint.isAntiAlias = aa != 0
            paint.strokeWidth = 8f
            paint.strokeCap = SkPaint.Cap.kSquare_Cap
            // Single dash element cut off start/end.
            drawLine(c, 32, 16, paint, finalX = 20f, phase = 5f)
            c.translate(0f, 20f)
            // Two cut-off dash elements.
            drawLine(c, 32, 16, paint, finalX = 56f, phase = 5f)
            c.translate(0f, 20f)
            // Many dashes, first and last cut off.
            drawLine(c, 32, 16, paint, finalX = 584f, phase = 5f)
            c.translate(0f, 20f)
            // Diagonal (non axis-aligned).
            drawLine(c, 32, 16, paint, finalX = 600f, finalY = 30f)
            c.translate(0f, 20f)
            // Off-only interval ⇒ nothing drawn.
            drawLine(c, 32, 16, paint, finalX = 8f, phase = 40f)
            c.translate(0f, 20f)
        }

        // Overlapping circles.
        c.translate(5f, 20f)
        paint.isAntiAlias = true
        paint.strokeCap = SkPaint.Cap.kRound_Cap
        paint.color = 0x44000000
        paint.strokeWidth = 40f
        drawLine(c, 0, 30, paint)

        c.translate(0f, 50f)
        paint.strokeCap = SkPaint.Cap.kSquare_Cap
        drawLine(c, 0, 30, paint)

        // Zero-length line caps.
        c.translate(0f, 50f)
        paint.strokeCap = SkPaint.Cap.kRound_Cap
        paint.color = 0xFF000000.toInt()
        paint.strokeWidth = 11f
        drawLine(c, 0, 30, paint, finalX = 0f)

        c.translate(100f, 0f)
        drawLine(c, 1, 30, paint, finalX = 0f)
    }
}
