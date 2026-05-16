package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp::Strokes5GM` (`zero_control_stroke`,
 * 400 × 800).
 *
 * Stress test for the stroker on curves with degenerate tangents at
 * `t=0` or `t=1` (skbug.com/40035337). Six paths : a cubic with the
 * second control point coinciding with the first, a "degenerate" quad
 * where the control point coincides with the end, a similar conic ;
 * and the sister-set with the degeneracy at the start instead. All
 * stroked at width 40 with `kButt_Cap`.
 */
public class ZeroControlStrokeGM : GM() {

    override fun getName(): String = "zero_control_stroke"
    override fun getISize(): SkISize = SkISize.Make(400, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 40f
            strokeCap = SkPaint.Cap.kButt_Cap
        }

        c.drawPath(
            SkPathBuilder()
                .moveTo(157.474f, 111.753f)
                .cubicTo(128.5f, 111.5f, 35.5f, 29.5f, 35.5f, 29.5f)
                .detach(),
            p,
        )
        c.drawPath(
            SkPathBuilder().moveTo(250f, 50f).quadTo(280f, 80f, 280f, 80f).detach(),
            p,
        )
        c.drawPath(
            SkPathBuilder().moveTo(150f, 50f).conicTo(180f, 80f, 180f, 80f, 0.707f).detach(),
            p,
        )

        c.drawPath(
            SkPathBuilder()
                .moveTo(157.474f, 311.753f)
                .cubicTo(157.474f, 311.753f, 85.5f, 229.5f, 35.5f, 229.5f)
                .detach(),
            p,
        )
        c.drawPath(
            SkPathBuilder().moveTo(280f, 250f).quadTo(280f, 250f, 310f, 280f).detach(),
            p,
        )
        c.drawPath(
            SkPathBuilder().moveTo(180f, 250f).conicTo(180f, 250f, 210f, 280f, 0.707f).detach(),
            p,
        )
    }
}
