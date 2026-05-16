package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/b_119394958.cpp`
 * (`DEF_SIMPLE_GM(b_119394958, canvas, 100, 100)`).
 *
 * Repro for an Android GPU bug — a stroked round-cap arc batched with
 * a filled circle was reading uninitialised vertex data. The render
 * itself is straightforward:
 *  - blue filled circle at (50, 50, r=45);
 *  - green stroked circle at (50, 50, r=35), strokeWidth = 5;
 *  - red stroked round-cap arc on `(30, 30, 70, 70)`, start = 0°,
 *    sweep = 110°, useCenter = `false`, strokeWidth = 5.
 *
 * Reference image: `b_119394958.png`, 100 × 100.
 *
 * Stresses :
 *  - mixed fill / stroke / arc with the same paint reused;
 *  - first GM in the suite to combine `drawArc(useCenter = false)`
 *    with `kRound_Cap`. Round-cap arc endpoints are emitted as half
 *    circles by the stroker, exercising the round-cap dispatch on a
 *    short-sweep curve.
 */
public class B119394958GM : GM() {

    override fun getName(): String = "b_119394958"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }
        c.drawCircle(50f, 50f, 45f, paint)

        paint.color = SK_ColorGREEN
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 5f
        c.drawCircle(50f, 50f, 35f, paint)

        paint.color = SK_ColorRED
        paint.strokeCap = SkPaint.Cap.kRound_Cap
        c.drawArc(SkRect.MakeLTRB(30f, 30f, 70f, 70f), 0f, 110f, false, paint)
    }
}
