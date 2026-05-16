package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/smallarc.cpp` (`DEF_SIMPLE_GM(smallarc, …)`).
 *
 * One red AA cubic-Bézier stroked at width 120 (huge), drawn under
 * `translate(-400, -400) ; scale(8, 8)` so the source-space ¾-arc lands as a
 * thick wedge in the centre of the 762 × 762 canvas.
 *
 * Source path: `moveTo(75, 0) cubicTo(33.5, 0, 0, 33.5, 0, 75)` — a single
 * cubic Bézier approximating a quarter-circle arc from (75, 0) to (0, 75)
 * with control points pulled in toward the origin. Stroking it at 120 px
 * with a 8× CTM scale yields a curved bar visible across most of the canvas.
 *
 * Hits the [SkStroker.resScale] code path (CTM scale = 8) end-to-end.
 *
 * Reference image: `smallarc.png`, 762 × 762, default white BG.
 */
public class SmallArcGM : GM() {

    override fun getName(): String = "smallarc"
    override fun getISize(): SkISize = SkISize.Make(762, 762)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(75f, 0f)
            .cubicTo(33.5f, 0f, 0f, 33.5f, 0f, 75f)
            .detach()

        val paint = SkPaint().apply {
            // SK_ColorRED = 0xFFFF0000.
            color = 0xFFFF0000.toInt()
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 120f
        }
        c.translate(-400f, -400f)
        c.scale(8f, 8f)
        c.drawPath(path, paint)
    }
}
