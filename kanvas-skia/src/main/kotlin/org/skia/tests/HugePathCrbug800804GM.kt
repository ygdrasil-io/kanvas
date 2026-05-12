package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/hugepath.cpp::DEF_SIMPLE_GM(path_huge_crbug_800804,
 * canvas, 50, 600)`.
 *
 * Exercises the path stroker on segments whose endpoints contain extreme
 * float values (≈ ±10²⁰). Three stroke widths are sampled
 * (`0.9 / 1.0 / 1.1`) so both the hairline and the proper-stroke code
 * paths are walked.
 *
 * C++ original — see `gm/hugepath.cpp`.
 */
public class HugePathCrbug800804GM : GM() {

    override fun getName(): String = "path_huge_crbug_800804"
    override fun getISize(): SkISize = SkISize.Make(50, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        val widths = floatArrayOf(0.9f, 1.0f, 1.1f)
        for (w in widths) {
            paint.strokeWidth = w
            // First near-vertical line with a huge Y endpoint.
            var path = SkPath.Line(-1000f to 12345678901234567890f, 10.5f to 200f)
            c.drawPath(path, paint)
            // Second near-vertical line with a huge negative Y endpoint.
            path = SkPath.Line(30.5f to 400f, 1000f to -9.8765432109876543210e+19f)
            c.drawPath(path, paint)
            c.translate(3f, 0f)
        }
    }
}
