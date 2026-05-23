package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/dashcircle.cpp::maddash` (DEF_SIMPLE_GM, 1600 × 1600).
 *
 * Three strokes layered at progressively translated origins:
 *
 *  1. A black filled background rect covering the full canvas.
 *  2. A red-stroked (width 380) circle at (400, 400) radius 200,
 *     dashed with `{2.5, 10}` intervals.
 *  3. A quadratic Bézier "loop" path at (800, 400) → (1000, 600) →
 *     (800, 800) → (600, 600) → close, stroked width 320 with the
 *     same dash effect (via a retained [SkPaint]).
 *  4. A cubic Bézier "loop" path (same bounding control points as #3
 *     but with cubic control points at x±100 from the on-curve
 *     endpoints), stroked width 300, translated to (250, 650) relative
 *     to the quad's origin.
 *
 * The GM is "mad" because it combines very large stroke widths with
 * a short dash-on interval (2.5) to expose degenerate dash-cap cases
 * in the stroker/dash-effect pipeline.
 */
public class MaddashGM : GM() {

    override fun getName(): String = "maddash"
    override fun getISize(): SkISize = SkISize.Make(1600, 1600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Black background.
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 1600f, 1600f), SkPaint())

        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 380f
        }

        val intvls = floatArrayOf(2.5f, 10f)
        p.pathEffect = SkDashPathEffect.Make(intvls, 0f)

        // Draw 1: dashed circle.
        c.drawCircle(400f, 400f, 200f, p)

        // Draw 2: quadratic Bézier "loop" — matches upstream's
        //   moveTo(800,400) quadTo(1000,400,1000,600)
        //   quadTo(1000,800,800,800) quadTo(600,800,600,600)
        //   quadTo(600,400,800,400) close
        // translated by (350, 150), stroke 320.
        val quadPath = SkPathBuilder()
            .moveTo(800f, 400f)
            .quadTo(1000f, 400f, 1000f, 600f)
            .quadTo(1000f, 800f, 800f, 800f)
            .quadTo(600f, 800f, 600f, 600f)
            .quadTo(600f, 400f, 800f, 400f)
            .close()
            .detach()
        c.translate(350f, 150f)
        p.strokeWidth = 320f
        c.drawPath(quadPath, p)

        // Draw 3: cubic Bézier "loop" — matches upstream's
        //   moveTo(800,400) cubicTo(900,400,1000,500,1000,600)
        //   cubicTo(1000,700,900,800,800,800)
        //   cubicTo(700,800,600,700,600,600)
        //   cubicTo(600,500,700,400,800,400) close
        // translated by (-550, 500) relative to draw-2 origin, stroke 300.
        val cubicPath = SkPathBuilder()
            .moveTo(800f, 400f)
            .cubicTo(900f, 400f, 1000f, 500f, 1000f, 600f)
            .cubicTo(1000f, 700f, 900f, 800f, 800f, 800f)
            .cubicTo(700f, 800f, 600f, 700f, 600f, 600f)
            .cubicTo(600f, 500f, 700f, 400f, 800f, 400f)
            .close()
            .detach()
        c.translate(-550f, 500f)
        p.strokeWidth = 300f
        c.drawPath(cubicPath, p)
    }
}
