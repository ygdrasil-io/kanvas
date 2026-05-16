package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/hugepath.cpp::path_huge_crbug_800804`
 * (DEF_SIMPLE_GM, 50 × 600).
 *
 * Strokes 2 lines per width with absurd endpoint coords (~ 10²⁰), at
 * 3 hairline-ish widths (0.9 / 1.0 / 1.1). Originally exposed an
 * overflow in the AA hair-line scan that produced artefacts when
 * coordinates exceeded the float range.
 */
public class PathHugeCrbug800804GM : GM() {

    override fun getName(): String = "path_huge_crbug_800804"
    override fun getISize(): SkISize = SkISize.Make(50, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        for (w in floatArrayOf(0.9f, 1.0f, 1.1f)) {
            paint.strokeWidth = w

            val pathA = SkPath.Line(-1000f to 12345678901234567890f, 10.5f to 200f)
            c.drawPath(pathA, paint)

            val pathB = SkPath.Line(30.5f to 400f, 1000f to -9.8765432109876543210e+19f)
            c.drawPath(pathB, paint)

            c.translate(3f, 0f)
        }
    }
}
