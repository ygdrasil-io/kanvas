package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkSpan
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRandom

/**
 * C++ original:
 * ```cpp
 * class PointsGM : public GM {
 * public:
 *     PointsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("points"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 490); }
 *
 *     static void fill_pts(SkSpan<SkPoint> pts, SkRandom* rand) {
 *         for (auto& p : pts) {
 *             // Compute these independently and store in variables, rather
 *             // than in the parameter-passing expression, to get consistent
 *             // evaluation order across compilers.
 *             SkScalar y = rand->nextUScalar1() * 480;
 *             SkScalar x = rand->nextUScalar1() * 640;
 *             p.set(x, y);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(SK_Scalar1, SK_Scalar1);
 *
 *         SkRandom rand;
 *         SkPaint  p0, p1, p2, p3;
 *         const size_t n = 99;
 *
 *         p0.setColor(SK_ColorRED);
 *         p1.setColor(SK_ColorGREEN);
 *         p2.setColor(SK_ColorBLUE);
 *         p3.setColor(SK_ColorWHITE);
 *
 *         p0.setStrokeWidth(SkIntToScalar(4));
 *         p2.setStrokeCap(SkPaint::kRound_Cap);
 *         p2.setStrokeWidth(SkIntToScalar(6));
 *
 *         std::vector<SkPoint> pts(n);
 *         fill_pts(pts, &rand);
 *
 *         canvas->drawPoints(SkCanvas::kPolygon_PointMode, pts, p0);
 *         canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p1);
 *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p2);
 *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p3);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PointsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("points"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 490); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(SK_Scalar1, SK_Scalar1);
   *
   *         SkRandom rand;
   *         SkPaint  p0, p1, p2, p3;
   *         const size_t n = 99;
   *
   *         p0.setColor(SK_ColorRED);
   *         p1.setColor(SK_ColorGREEN);
   *         p2.setColor(SK_ColorBLUE);
   *         p3.setColor(SK_ColorWHITE);
   *
   *         p0.setStrokeWidth(SkIntToScalar(4));
   *         p2.setStrokeCap(SkPaint::kRound_Cap);
   *         p2.setStrokeWidth(SkIntToScalar(6));
   *
   *         std::vector<SkPoint> pts(n);
   *         fill_pts(pts, &rand);
   *
   *         canvas->drawPoints(SkCanvas::kPolygon_PointMode, pts, p0);
   *         canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p1);
   *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p2);
   *         canvas->drawPoints(SkCanvas::kPoints_PointMode, pts, p3);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void fill_pts(SkSpan<SkPoint> pts, SkRandom* rand) {
     *         for (auto& p : pts) {
     *             // Compute these independently and store in variables, rather
     *             // than in the parameter-passing expression, to get consistent
     *             // evaluation order across compilers.
     *             SkScalar y = rand->nextUScalar1() * 480;
     *             SkScalar x = rand->nextUScalar1() * 640;
     *             p.set(x, y);
     *         }
     *     }
     * ```
     */
    protected fun fillPts(pts: SkSpan<SkPoint>, rand: SkRandom?) {
      TODO("Implement fillPts")
    }
  }
}
