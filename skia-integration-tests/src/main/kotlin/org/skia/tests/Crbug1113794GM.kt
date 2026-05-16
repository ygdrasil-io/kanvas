package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1113794.cpp::crbug_1113794` (600 × 200).
 *
 * Vertical line `(50, 80) → (50, 20)` stroked with width 0.25 px, AA,
 * dashed `[10, 10]` phase 0, then drawn under a viewBox `RectToRect`
 * matrix that maps `100×100` → `600×200`. Reproduces a Chrome bug
 * where the dasher mis-handled sub-pixel-wide strokes under non-uniform
 * scale.
 */
public class Crbug1113794GM : GM() {

    override fun getName(): String = "crbug_1113794"
    override fun getISize(): SkISize = SkISize.Make(600, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPath.Line(50f to 80f, 50f to 20f)

        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            strokeWidth = 0.25f
            style = SkPaint.Style.kStroke_Style
            pathEffect = SkDashPathEffect.Make(floatArrayOf(10f, 10f), 0f)
        }

        val viewBox = SkMatrix.MakeRectToRect(
            SkRect.MakeWH(100f, 100f),
            SkRect.MakeWH(600f, 200f),
        ) ?: SkMatrix.Identity
        c.concat(viewBox)
        c.drawPath(path, paint)
    }
}
