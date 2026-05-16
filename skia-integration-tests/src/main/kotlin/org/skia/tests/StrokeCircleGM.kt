package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/addarc.cpp:StrokeCircleGM`.
 *
 * Concentric stroked ovals at decreasing size (24×24 down to ~1.5×1.5
 * source-units) drawn under a `scale(20, 20)` CTM. Stroke width is
 * `0.5` source-units (= 10 device-px), and each iteration insets the
 * rect by `delta = 0.75` (1.5 device-px).
 *
 * Upstream rotates each oval by `fRotate * sign` (sign alternating per
 * iteration) — `fRotate` is 0 in the static GM dump, so every rotate is
 * a no-op. We keep the call (now that [SkCanvas.rotate] exists) for
 * structural fidelity, dispatch-tested by [SkMatrixTest].
 *
 * Reference image: `strokecircle.png`, 520 × 520, default white BG.
 *
 * Stresses the resScale-aware stroker (Phase 3i) on conic curves at
 * 20× CTM scale — the inverse of `Strokes4GM`'s circle stroke.
 */
public class StrokeCircleGM : GM() {

    override fun getName(): String = "strokecircle"
    override fun getISize(): SkISize = SkISize.Make(520, 520)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(20f, 20f)
        c.translate(13f, 13f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0.5f       // SK_Scalar1 / 2
        }

        val delta = paint.strokeWidth * 3f / 2f
        var r = SkRect.MakeXYWH(-12f, -12f, 24f, 24f)
        val rand = SkRandom()

        // fRotate=0 in static dump → rotate(0) is identity. Still call it
        // to exercise the SkCanvas.rotate dispatch end-to-end.
        val fRotate = 0f
        var sign = 1f
        while (r.width() > paint.strokeWidth * 2f) {
            // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);`.
            c.withSave {
                rotate(fRotate * sign)
                paint.color = ToolUtils.colorTo565(rand.nextU() or 0xFF000000.toInt())
                drawOval(r, paint)
            }
            r = SkRect.MakeLTRB(
                r.left + delta, r.top + delta,
                r.right - delta, r.bottom - delta,
            )
            sign = -sign
        }
    }
}
