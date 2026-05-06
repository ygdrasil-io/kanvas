package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_general` (800 × 800).
 *
 * Three test configurations sharing an 8-pointed star path
 * (`R = 115.2`, `C = 128`, 8 points stepping through `2.6927937·i`
 * radians) :
 *
 *  1. Filled, rotated 1°.
 *  2. Stroked (width 5), rotated 1°, translated +200x.
 *  3. Two pairs of touching rects that abut at a fractional column —
 *     used to validate cumulative alpha across a shared seam.
 */
public class AnalyticAntialiasGeneralGM : GM() {

    override fun getName(): String = "analytic_antialias_general"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.clear(SK_ColorWHITE)

        c.save()
        c.rotate(1f)
        val r = 115.2f
        val cc = 128.0f
        val builder = SkPathBuilder()
        builder.moveTo(cc + r, cc)
        for (i in 1 until 8) {
            val a = 2.6927937f * i
            builder.lineTo(cc + r * cos(a), cc + r * sin(a))
        }
        val path = builder.detach()
        c.drawPath(path, p)
        c.restore()

        c.save()
        c.translate(200f, 0f)
        c.rotate(1f)
        p.style = SkPaint.Style.kStroke_Style
        p.strokeWidth = 5f
        c.drawPath(path, p)
        c.restore()

        // Two paths abutting at fractional pixel columns — alpha accumulation.
        p.style = SkPaint.Style.kFill_Style
        c.translate(0f, 300f)
        c.drawPath(
            SkPathBuilder()
                .addRect(SkRect.MakeLTRB(20f, 20f, 100.4999f, 100f))
                .addRect(SkRect.MakeLTRB(100.5001f, 20f, 200f, 100f))
                .detach(),
            p,
        )

        c.translate(300f, 0f)
        c.drawPath(
            SkPathBuilder()
                .addRect(SkRect.MakeLTRB(20f, 20f, 100.1f, 100f))
                .addRect(SkRect.MakeLTRB(100.9f, 20f, 200f, 100f))
                .detach(),
            p,
        )
    }
}
