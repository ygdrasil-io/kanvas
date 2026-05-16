package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/addarc.cpp:AddArcGM`.
 *
 * Concentric stroked open arcs of `345°` sweep, randomly rotated and
 * insets by `strokeWidth + 4` per iteration, until the rect would no
 * longer fit two stroke widths across. The path is built via
 * [SkPathBuilder.addArc] (oval + start + sweep) — exercising the
 * cubic-Bézier arc emitter end-to-end.
 *
 * Upstream's `onAnimate` sets `fRotate` based on time; the static GM
 * dump runs with `fRotate = 0`, making the per-iteration angle offset
 * `startAngle += fRotate * 360 * speed * sign` collapse to zero. The
 * baseline `startAngle = rand.nextUScalar1() * 360` still drives an
 * unpredictable arc orientation per iteration, but matches upstream
 * because [SkRandom] is bit-compatible.
 *
 * Reference image: `addarc.png`, 1040 × 1040, default white BG.
 */
public class AddArcGM : GM() {

    override fun getName(): String = "addarc"
    override fun getISize(): SkISize = SkISize.Make(1040, 1040)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(20f, 20f)

        var r = SkRect.MakeWH(1000f, 1000f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 15f
        }

        val inset = paint.strokeWidth + 4f
        val sweepAngle = 345f
        val rand = SkRandom()

        var sign = 1f
        while (r.width() > paint.strokeWidth * 3f) {
            paint.color = ToolUtils.colorTo565(rand.nextU() or 0xFF000000.toInt())
            val startAngle = rand.nextUScalar1() * 360f
            // fRotate=0 in static dump; the speed-scaled offset is zero.

            val path = SkPathBuilder().addArc(r, startAngle, sweepAngle).detach()
            c.drawPath(path, paint)

            r = SkRect.MakeLTRB(r.left + inset, r.top + inset,
                                r.right - inset, r.bottom - inset)
            sign = -sign
        }
    }
}
