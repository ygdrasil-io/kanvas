package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/points.cpp::points_maskfilter`
 * (GM registered name `points_maskfilter`, `DEF_SIMPLE_GM` form).
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(points_maskfilter, canvas, 512, 256) {
 *     constexpr int N = 30;
 *     SkPoint pts[N];
 *
 *     SkRandom rand;
 *     for (SkPoint& p : pts) {
 *         p.fX = rand.nextF() * 220 + 18;
 *         p.fY = rand.nextF() * 220 + 18;
 *     }
 *
 *     auto mf = SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 6);
 *     const SkPaint::Cap caps[] = { SkPaint::kSquare_Cap, SkPaint::kRound_Cap };
 *
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setStroke(true);
 *     paint.setStrokeWidth(10);
 *
 *     for (auto cap : caps) {
 *         paint.setStrokeCap(cap);
 *
 *         paint.setMaskFilter(mf);
 *         paint.setColor(SK_ColorBLACK);
 *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, paint);
 *
 *         paint.setMaskFilter(nullptr);
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, paint);
 *
 *         canvas->translate(256, 0);
 *     }
 * }
 * ```
 *
 * Two 256-px-wide columns side-by-side : the same 30 random points
 * stamped first as fat black blurred discs (σ = 6, normal blur,
 * mask-filter shadow) and then as red unblurred discs on top. The
 * left column uses square strokes, the right column round.
 */
public class PointsMaskFilterGM : GM() {
    override fun getName(): String = "points_maskfilter"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val n = 30
        val pts = Array(n) { SkPoint(0f, 0f) }

        val rand = SkRandom()
        for (p in pts) {
            p.fX = rand.nextF() * 220f + 18f
            p.fY = rand.nextF() * 220f + 18f
        }

        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 6f)
        val caps = arrayOf(SkPaint.Cap.kSquare_Cap, SkPaint.Cap.kRound_Cap)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
        }

        for (cap in caps) {
            paint.strokeCap = cap

            paint.maskFilter = mf
            paint.color = SK_ColorBLACK
            c.drawPoints(SkCanvas.PointMode.kPoints, pts, paint)

            paint.maskFilter = null
            paint.color = SK_ColorRED
            c.drawPoints(SkCanvas.PointMode.kPoints, pts, paint)

            c.translate(256f, 0f)
        }
    }
}
