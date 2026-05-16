package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp` `DEF_SIMPLE_GM(CubicStroke, …)`.
 *
 * Three near-identical stroked cubics with `strokeWidth ∈
 * {1.0720, 1.0721, 1.0722}` (sub-1 % difference), translated 10 px
 * apart. The cubic spans `(-6000, -6000)` → `(2500, -6500)` with two
 * control points, so the 384 × 384 canvas only sees a small slice —
 * the test stresses the stroker's behaviour for very-near-floating-
 * point-equal stroke widths.
 *
 * Reference image: `CubicStroke.png`, 384 × 384, default white BG.
 */
public class CubicStrokeGM : GM() {

    override fun getName(): String = "CubicStroke"
    override fun getISize(): SkISize = SkISize.Make(384, 384)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1.0720f
        }
        val path = SkPathBuilder()
            .moveTo(-6000f, -6000f)
            .cubicTo(-3500f, 5500f, -500f, 5500f, 2500f, -6500f)
            .detach()
        c.drawPath(path, p)

        p.strokeWidth = 1.0721f
        c.translate(10f, 10f)
        c.drawPath(path, p)

        p.strokeWidth = 1.0722f
        c.translate(10f, 10f)
        c.drawPath(path, p)
    }
}
