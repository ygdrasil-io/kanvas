package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/fillcircle.cpp` (`FillCircleGM`).
 *
 * A spiralling stack of concentric AA ovals, each filled with a deterministic
 * 565-quantised random colour, drawn under `scale(20, 20) ; translate(13, 13)`.
 * The loop starts with the largest oval (24 × 24 in source space, 480 × 480 in
 * device space) and insets by `delta = strokeWidth * 3/2 = 0.75` source-units
 * each iteration until it would no longer fit twice the stroke width across.
 *
 * Note: upstream has a per-iteration `canvas->rotate(fRotate * sign)` driven
 * by `onAnimate`. Static GM dumps run with `fRotate = 0`, making the rotate
 * call a no-op — which is what the reference image was captured with. We
 * skip the call entirely (no `SkCanvas.rotate` in our matrix surface yet).
 *
 * Reference image: `fillcircle.png`, 520 × 520, default white BG.
 */
public class FillCircleGM : GM() {

    override fun getName(): String = "fillcircle"
    override fun getISize(): SkISize = SkISize.Make(520, 520)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(20f, 20f)
        c.translate(13f, 13f)

        // Upstream creates `paint` with `setStroke(true)` purely to use
        // `getStrokeWidth()` for the inset delta, then resets to fill via
        // `setStroke(false)` before drawing. We bypass the round-trip.
        val strokeWidth = 0.5f      // SK_Scalar1 / 2
        val delta = strokeWidth * 3f / 2f
        var r = SkRect.MakeXYWH(-12f, -12f, 24f, 24f)
        val rand = SkRandom()

        val paint = SkPaint().apply {
            isAntiAlias = true
            // style = kFill_Style (default) — upstream's `setStroke(false)`.
        }

        // `sign` flips per iteration in upstream (paired with the rotate),
        // but with `fRotate = 0` it has no observable effect — keep the
        // bookkeeping anyway so the rand consumption stays in lockstep.
        @Suppress("UNUSED_VARIABLE")
        var sign = 1f
        while (r.width() > strokeWidth * 2f) {
            c.save()
            // Upstream: canvas.rotate(fRotate * sign) — fRotate=0 → no-op.
            paint.color = ToolUtils.colorTo565(rand.nextU() or (0xFF shl 24))
            c.drawOval(r, paint)
            r = SkRect.MakeLTRB(r.left + delta, r.top + delta,
                                r.right - delta, r.bottom - delta)
            sign = -sign
            c.restore()
        }
    }
}
