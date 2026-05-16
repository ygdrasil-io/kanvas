package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_convex` (800 × 800).
 *
 * Stresses the analytic-AA convex-fill path with three configurations :
 *
 *  1. A 1°-rotated 180×180 rect at top-left.
 *  2. A row of two ultra-thin rects (0.2 px wide, 0.1 px tall) and a
 *     30 px circle, also rotated 1°.
 *  3. An "empty" but degenerate cubic path that exercises crbug.com/662914
 *     edge handling.
 *  4. A skbug 40038820 case — 4-vertex polygon hugging a fractional
 *     boundary.
 *  5. A skbug 40039068 case — 10-px-wide tall vertical strip hugging
 *     the 800-tall canvas's tile boundaries (T8888 splits at ~266 / ~534).
 */
public class AnalyticAntialiasConvexGM : GM() {

    override fun getName(): String = "analytic_antialias_convex"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.clear(SK_ColorWHITE)
        c.save()

        var y = 0f

        c.translate(0f, y)
        c.rotate(1f)
        c.drawRect(SkRect.MakeLTRB(20f, 20f, 200f, 200f), p)
        c.restore()

        y += 200f

        c.save()
        c.translate(0f, y)
        c.rotate(1f)
        c.drawRect(SkRect.MakeLTRB(20f, 20f, 20.2f, 200f), p)
        c.drawRect(SkRect.MakeLTRB(20f, 200f, 200f, 200.1f), p)
        c.drawCircle(100f, 100f, 30f, p)
        c.restore()

        // crbug.com/662914 — empty cubic path.
        var pb = SkPathBuilder()
        pb.moveTo(java.lang.Float.intBitsToFloat(0x429b9d5c), java.lang.Float.intBitsToFloat(0x4367a041))
        pb.cubicTo(
            java.lang.Float.intBitsToFloat(0x429b9d71), java.lang.Float.intBitsToFloat(0x4367a022),
            java.lang.Float.intBitsToFloat(0x429b9d64), java.lang.Float.intBitsToFloat(0x4367a009),
            java.lang.Float.intBitsToFloat(0x429b9d50), java.lang.Float.intBitsToFloat(0x43679ff2),
        )
        pb.lineTo(java.lang.Float.intBitsToFloat(0x429b9d5c), java.lang.Float.intBitsToFloat(0x4367a041))
        pb.close()
        c.drawPath(pb.detach(), p)

        // skbug.com/40038820
        y += 200f
        c.save()
        c.translate(0f, y)
        p.isAntiAlias = true
        pb = SkPathBuilder()
        pb.moveTo(1.98009784f, 9.0162744f)
        pb.lineTo(47.843992f, 10.1922744f)
        pb.lineTo(47.804008f, 11.7597256f)
        pb.lineTo(1.93990216f, 10.5837256f)
        c.drawPath(pb.detach(), p)
        c.restore()

        // skbug.com/40039068 — t8888 splits an 800-high canvas into 3 tiles.
        pb = SkPathBuilder()
        pb.moveTo(700f, 266f)
        pb.lineTo(710f, 266f)
        pb.lineTo(710f, 534f)
        pb.lineTo(700f, 534f)
        c.drawPath(pb.detach(), p)
    }
}
