package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/skbug_9319.cpp::skbug_9319` (256 × 512).
 *
 * Reproduces a bug where the outer portion of the GPU rect-blur was
 * too dark for very small sigmas. The trick : `clipX(rect, kDifference)`
 * cuts the **interior** of the shape out of the clip, then
 * `drawX(rect, paint{maskFilter=BlurNormal, σ=0.5})` draws the same
 * shape — only the **blurred halo** outside the original rect/rrect
 * survives. Visualises whether the halo's intensity matches across the
 * rectangular and RRect kinds.
 *
 * Two cells :
 *  1. clipRect(r, kDifference) + drawRect(r, p)  — rect halo only.
 *  2. clipRRect(rr, kDifference) + drawRRect(rr, p) — rrect halo only.
 */
public class Skbug9319GM : GM() {

    override fun getName(): String = "skbug_9319"
    override fun getISize(): SkISize = SkISize.Make(256, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 0.5f)
        }

        val r = SkRect.MakeXYWH(10f, 10f, 100f, 100f)

        // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);` (×2).
        c.withSave {
            clipRect(r, SkClipOp.kDifference)
            drawRect(r, p)
        }

        c.translate(0f, 120f)

        c.withSave {
            val rr = SkRRect.MakeRectXY(r, 0.1f, 0.1f)
            clipRRect(rr, SkClipOp.kDifference)
            drawRRect(rr, p)
        }
    }
}
