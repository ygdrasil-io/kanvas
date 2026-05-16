package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorWHITE
import org.skia.math.SK_ColorYELLOW
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/testgradient.cpp` (`TestGradientGM`,
 * GM name `testgradient`).
 *
 * Mixed multi-primitive smoke test :
 *  - a filled rect with a 0..256 / 0..256 blue→yellow `kClamp` linear
 *    gradient, covering only its own 100×160 rect (so the gradient is
 *    sampled across roughly ~37→62% of `t`);
 *  - a filled `RRect` constructed via `setOval` on the same rect then
 *    offset by (40, 80) — i.e. an axis-aligned ellipse;
 *  - a filled circle at `(180, 50)` of radius 25;
 *  - a stroked `RoundRect` (after offsetting the rect by (80, 50))
 *    with corner radii (10, 10), strokeWidth 4, AA on.
 *
 * The canvas is `drawColor`-ed white before any draw, so the bg is the
 * default white (matches upstream's explicit `drawColor(SK_ColorWHITE)`).
 *
 * Reference image: `testgradient.png`, 800 × 800.
 *
 * Stresses :
 *  - Linear gradient on a partial sweep (`t` in (~0.04, ~0.43)) — first
 *    test exercising a sub-segment lookup of the gradient interval map;
 *  - drawRRect oval-shape dispatch (radii equal to half-extents → kOval);
 *  - drawCircle, drawRoundRect (kSimple_Type), drawRect — all on the
 *    same canvas in one frame, with mixed solid + gradient paints.
 */
public class TestGradientGM : GM() {

    override fun getName(): String = "testgradient"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Match upstream's explicit canvas->drawColor(SK_ColorWHITE).
        c.drawColor(SK_ColorWHITE)

        val basePaint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
            strokeWidth = 4f
            color = 0xFFFE938C.toInt()
        }

        val rect = SkRect.MakeXYWH(10f, 10f, 100f, 160f)

        // Gradient-shaded rect. Linear blue→yellow from (0,0) to (256,256),
        // kClamp. The rect is a 100×160 region at (10, 10), so only a
        // sub-segment of t in [0..1] lights up.
        val gradPaint = basePaint.copy().apply {
            shader = SkLinearGradient.Make(
                p0 = SkPoint(0f, 0f),
                p1 = SkPoint(256f, 256f),
                colors = intArrayOf(SK_ColorBLUE, SK_ColorYELLOW),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
        }
        c.drawRect(rect, gradPaint)

        // RRect with setOval(rect) then offset(40, 80). Equivalent to
        // an oval bounded by the offset rect (radii are half-extents).
        val ovalRect = SkRect.MakeXYWH(rect.left + 40f, rect.top + 80f, rect.width(), rect.height())
        val oval = SkRRect.MakeOval(ovalRect)
        val solidPaint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
            strokeWidth = 4f
            color = 0xFFE6B89C.toInt()
        }
        c.drawRRect(oval, solidPaint)

        solidPaint.color = 0xFF9CAFB7.toInt()
        c.drawCircle(180f, 50f, 25f, solidPaint)

        // rect.offset(80, 50)
        rect.offset(80f, 50f)
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = 4f
            color = 0xFF4281A4.toInt()
            style = SkPaint.Style.kStroke_Style
        }
        c.drawRoundRect(rect, 10f, 10f, strokePaint)
    }
}
