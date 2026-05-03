package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/clip_strokerect.cpp` (`ClipStrokeRectGM`).
 *
 * Two AA-stroked red rects, each clipped to a tight horizontal band that
 * cuts deep into the stroke, plus a non-AA blue 2-pixel stroke marking the
 * clip rect itself. Exercises:
 *
 *   - AA-stroke coverage on rect edges (`strokeWidth = 22`, fully AA).
 *   - Clip interaction with AA fragments.
 *   - Non-AA hairline-ish stroke (width 2) on a thin rect.
 */
public class ClipStrokeRectGM : GM() {
    override fun getName(): String = "clip_strokerect"
    override fun getISize(): SkISize = SkISize.Make(200, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 22f
        }

        val r1 = SkRect.MakeXYWH(20f, 20f, 100f, 100f)
        // Setting the height of `rect1` to 19 causes failure (per upstream comment) —
        // the band must straddle the stroke evenly to expose the bug this GM tests.
        val rect1 = SkRect.MakeXYWH(20f, 0f, 100f, 20f)

        c.save()
        c.clipRect(rect1, doAntiAlias = true)
        c.drawRect(r1, p)
        c.restore()

        p.color = SK_ColorBLUE
        p.strokeWidth = 2f
        c.drawRect(rect1, p)

        p.color = SK_ColorRED
        p.isAntiAlias = true
        p.style = SkPaint.Style.kStroke_Style
        p.strokeWidth = 22f

        val r2 = SkRect.MakeXYWH(20f, 140f, 100f, 100f)
        // Setting the height of `rect2` to 19 causes failure (per upstream comment).
        val rect2 = SkRect.MakeXYWH(20f, 120f, 100f, 19f)

        c.save()
        c.clipRect(rect2, doAntiAlias = true)
        c.drawRect(r2, p)
        c.restore()

        p.color = SK_ColorBLUE
        p.strokeWidth = 2f
        c.drawRect(rect2, p)
    }
}
