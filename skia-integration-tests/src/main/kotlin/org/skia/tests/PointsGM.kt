package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/points.cpp::PointsGM` (GM registered name
 * `points`).
 *
 * C++ original :
 * ```cpp
 * SkString getName() const override { return SkString("points"); }
 * SkISize getISize() override { return SkISize::Make(640, 490); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->translate(SK_Scalar1, SK_Scalar1);
 *     SkRandom rand;
 *     SkPaint  p0, p1, p2, p3;
 *     const size_t n = 99;
 *     p0.setColor(SK_ColorRED);
 *     p1.setColor(SK_ColorGREEN);
 *     p2.setColor(SK_ColorBLUE);
 *     p3.setColor(SK_ColorWHITE);
 *     p0.setStrokeWidth(SkIntToScalar(4));
 *     p2.setStrokeCap(SkPaint::kRound_Cap);
 *     p2.setStrokeWidth(SkIntToScalar(6));
 *     std::vector<SkPoint> pts(n);
 *     fill_pts(pts, &rand);
 *     canvas->drawPoints(SkCanvas::kPolygon_PointMode, pts, p0);
 *     canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p1);
 *     canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p2);
 *     canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p3);
 * }
 * ```
 *
 * The 99 random points are filled in the upstream order — `y` then
 * `x` — both via `SkRandom::nextUScalar1`, so our bit-compatible
 * [SkRandom] reproduces the same point cloud as the reference.
 *
 * Four passes :
 *  - **kPolygon** with a 4-px red stroke connects consecutive points.
 *  - **kLines** with default (hairline) green stroke draws short
 *    segments between adjacent pairs.
 *  - **kPoints** with a 6-px blue round-cap paint stamps a circle per
 *    point.
 *  - **kPoints** with a default white paint paints a single-pixel dot
 *    on top of each blue circle (butt cap, hairline — visible as a
 *    1×1 fill at the point centres in our raster).
 */
public class PointsGM : GM() {
    override fun getName(): String = "points"
    override fun getISize(): SkISize = SkISize.Make(640, 490)

    private fun fillPts(pts: Array<SkPoint>, rand: SkRandom) {
        for (i in pts.indices) {
            // Compute y first then x — matches upstream's "store in
            // variables before passing to set()" workaround for
            // cross-compiler evaluation order.
            val y = rand.nextUScalar1() * 480f
            val x = rand.nextUScalar1() * 640f
            pts[i].set(x, y)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(1f, 1f)

        val rand = SkRandom()
        val p0 = SkPaint()
        val p1 = SkPaint()
        val p2 = SkPaint()
        val p3 = SkPaint()
        val n = 99

        p0.color = SK_ColorRED
        p1.color = SK_ColorGREEN
        p2.color = SK_ColorBLUE
        p3.color = SK_ColorWHITE

        p0.strokeWidth = 4f
        p2.strokeCap = SkPaint.Cap.kRound_Cap
        p2.strokeWidth = 6f

        val pts = Array(n) { SkPoint(0f, 0f) }
        fillPts(pts, rand)

        c.drawPoints(SkCanvas.PointMode.kPolygon, pts, p0)
        c.drawPoints(SkCanvas.PointMode.kLines, pts, p1)
        c.drawPoints(SkCanvas.PointMode.kPoints, pts, p2)
        c.drawPoints(SkCanvas.PointMode.kPoints, pts, p3)
    }
}
