package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/nested.cpp::nested_hairline_square`
 * (DEF_SIMPLE_GM, 64 × 64).
 *
 * Regression test for crbug.com/1234194: 1 row of 3 stroked squares
 * drawn at subpixel scale, with a second 0.5 px-shifted row below.
 * The nested path is an outer 5×5 rect containing a 3×3 rect (kCCW),
 * scaled down from a 24×24 viewbox into a 16×16 div (scale 16/24).
 */
public class NestedHairlineSquareGM : GM() {

    override fun getName(): String = "nested_hairline_square"
    override fun getISize(): SkISize = SkISize.Make(64, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val square: SkPath = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(0f, 9f, 5f, 14f))
            .addRect(SkRect.MakeLTRB(1f, 10f, 4f, 13f), SkPathDirection.kCCW)
            .detach()

        val paint = SkPaint().apply {
            color = SkColorSetARGB(255, 70, 70, 70)
            isAntiAlias = true
        }

        val drawEllipses: () -> Unit = {
            c.save()
            // Scale the 24×24 SVG viewbox down to a 16×16 div.
            c.scale(16f / 24f, 16f / 24f)
            c.drawPath(square, paint)
            c.translate(10f, 0f)
            c.drawPath(square, paint)
            c.translate(10f, 0f)
            c.drawPath(square, paint)
            c.restore()
        }

        drawEllipses()
        c.translate(0.5f, 16f)
        drawEllipses()
    }
}
