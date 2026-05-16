package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/hairlines.cpp::squarehair` (DEF_SIMPLE_GM,
 * 240 × 360).
 *
 * Hairline / thin-stroke regression matrix. For each combination of
 *   - 4 stroke widths : `{0, 0.999, 1, 1.001}`
 *   - 3 caps : `{Butt, Square, Round}`
 *   - 2 AA modes : on / off
 * draws a small set of primitives (3 lines + a moveTo / quadTo / conicTo
 * path + a moveTo / cubicTo / lineTo path) translated 30 px down per
 * cap iteration. The 4 widths × 3 caps stacked vertically per AA pane
 * (2 panes side-by-side, 120 px apart).
 *
 * Originally exposed a near-1px-stroke rounding bug in the AA hairline
 * scan when widths fell either side of `1.0` exactly.
 */
public class SquareHairGM : GM() {

    override fun getName(): String = "squarehair"
    override fun getISize(): SkISize = SkISize.Make(240, 360)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val widths = floatArrayOf(0f, 0.999f, 1f, 1.001f)
        val caps = arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kSquare_Cap, SkPaint.Cap.kRound_Cap)
        for (alias in booleanArrayOf(false, true)) {
            c.save()
            for (width in widths) {
                for (cap in caps) {
                    drawSquareHairTests(c, width, cap, alias)
                }
            }
            c.restore()
            c.translate(120f, 0f)
        }
    }

    private fun drawSquareHairTests(canvas: SkCanvas, width: Float, cap: SkPaint.Cap, aa: Boolean) {
        val paint = SkPaint().apply {
            strokeCap = cap
            strokeWidth = width
            isAntiAlias = aa
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawLine(10f, 10f, 20f, 10f, paint)
        canvas.drawLine(30f, 10f, 30f, 20f, paint)
        canvas.drawLine(40f, 10f, 50f, 20f, paint)

        val pathA = SkPathBuilder()
            .moveTo(60f, 10f)
            .quadTo(60f, 20f, 70f, 20f)
            .conicTo(70f, 10f, 80f, 10f, 0.707f)
            .detach()
        canvas.drawPath(pathA, paint)

        val pathB = SkPathBuilder()
            .moveTo(90f, 10f)
            .cubicTo(90f, 20f, 100f, 20f, 100f, 10f)
            .lineTo(110f, 10f)
            .detach()
        canvas.drawPath(pathB, paint)

        canvas.translate(0f, 30f)
    }
}
