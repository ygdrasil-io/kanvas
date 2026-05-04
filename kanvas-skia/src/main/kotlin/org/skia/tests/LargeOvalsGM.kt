package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/conicpaths.cpp` `DEF_SIMPLE_GM(largeovals, …)`.
 *
 * Two pairs of stroked / stroke-and-filled large ovals (5000 × 4000)
 * — first axis-aligned (testing the upstream `EllipseOp` GPU path),
 * then rotated 1° (testing the upstream `DIEllipseOp` near-axis-aligned
 * path). The 250 × 250 canvas sees only a tiny clipped fragment of
 * each oval's circumference; the test is whether those clipped
 * fragments rasterize without pixel-blur or coverage banding.
 *
 * Reference image: `largeovals.png`, 250 × 250, default white BG.
 *
 * Stresses [SkCanvas.drawOval] under huge radii (heavy clipping at the
 * canvas borders) plus a 1°-rotation CTM.
 */
public class LargeOvalsGM : GM() {

    override fun getName(): String = "largeovals"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // EllipseOp test — large axis-aligned oval.
        var r = SkRect.MakeXYWH(-520f, -520f, 5000f, 4000f)
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 100f
        }
        c.drawOval(r, paint)
        r.offset(-15f, -15f)
        paint.color = 0xFF444444.toInt()       // SK_ColorDKGRAY
        // Stroke-and-fill avoids the upstream "SimpleFill" fast path.
        paint.style = SkPaint.Style.kStrokeAndFill_Style
        paint.strokeWidth = 1f
        c.drawOval(r, paint)

        // DIEllipseOp test — same shape under a 1°-rotation CTM.
        c.rotate(1.0f)
        r.offset(55f, 55f)
        paint.color = 0xFF888888.toInt()       // SK_ColorGRAY
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 100f
        c.drawOval(r, paint)
        r.offset(-15f, -15f)
        paint.color = 0xFFCCCCCC.toInt()       // SK_ColorLTGRAY
        paint.style = SkPaint.Style.kStrokeAndFill_Style
        paint.strokeWidth = 1f
        c.drawOval(r, paint)
    }
}
