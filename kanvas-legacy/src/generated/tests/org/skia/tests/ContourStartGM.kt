package org.skia.tests

import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkPathDirection
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ContourStartGM : public GM {
 * protected:
 *     void onOnceBeforeDraw() override {
 *         const SkScalar kMaxDashLen = 100;
 *         const SkScalar kDashGrowth = 1.2f;
 *
 *         STArray<100, SkScalar> intervals;
 *         for (SkScalar len = 1; len < kMaxDashLen; len *= kDashGrowth) {
 *             intervals.push_back(len);
 *             intervals.push_back(len);
 *         }
 *
 *         fDashPaint.setAntiAlias(true);
 *         fDashPaint.setStyle(SkPaint::kStroke_Style);
 *         fDashPaint.setStrokeWidth(6);
 *         fDashPaint.setColor(0xff008000);
 *         fDashPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0));
 *
 *         fPointsPaint.setColor(0xff800000);
 *         fPointsPaint.setStrokeWidth(3);
 *
 *         fRect = SkRect::MakeLTRB(10, 10, 100, 70);
 *     }
 *
 *     SkString getName() const override { return SkString("contour_start"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kImageWidth, kImageHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *             return SkPath::Rect(rect, dir, startIndex);
 *         });
 *
 *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *             return SkPath::Oval(rect, dir, startIndex);
 *         });
 *
 *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *             SkRRect rrect;
 *             const SkVector radii[4] = { {15, 15}, {15, 15}, {15, 15}, {15, 15}};
 *             rrect.setRectRadii(rect, radii);
 *             return SkPath::RRect(rrect, dir, startIndex);
 *         });
 *
 *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *             SkRRect rrect;
 *             rrect.setRect(rect);
 *             return SkPath::RRect(rrect, dir, startIndex);
 *         });
 *
 *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
 *             SkRRect rrect;
 *             rrect.setOval(rect);
 *             return SkPath::RRect(rrect, dir, startIndex);
 *         });
 *
 *     }
 *
 * private:
 *     inline static constexpr int kImageWidth = 1200;
 *     inline static constexpr int kImageHeight = 600;
 *
 *     SkPaint fDashPaint, fPointsPaint;
 *     SkRect  fRect;
 *
 *     void drawDirs(SkCanvas* canvas,
 *                   SkPath (*makePath)(const SkRect&, SkPathDirection, unsigned)) const {
 *         drawOneColumn(canvas, SkPathDirection::kCW, makePath);
 *         canvas->translate(kImageWidth / 10, 0);
 *         drawOneColumn(canvas, SkPathDirection::kCCW, makePath);
 *         canvas->translate(kImageWidth / 10, 0);
 *     }
 *
 *     void drawOneColumn(SkCanvas* canvas, SkPathDirection dir,
 *                        SkPath (*makePath)(const SkRect&, SkPathDirection, unsigned)) const {
 *         SkAutoCanvasRestore acr(canvas, true);
 *
 *         for (unsigned i = 0; i < 8; ++i) {
 *             const SkPath path = makePath(fRect, dir, i);
 *             canvas->drawPath(path, fDashPaint);
 *             canvas->drawPoints(SkCanvas::kPoints_PointMode, path.points(), fPointsPaint);
 *
 *             canvas->translate(0, kImageHeight / 8);
 *         }
 *     }
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ContourStartGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageWidth = 1200
   * ```
   */
  private var fDashPaint: SkPaint = TODO("Initialize fDashPaint")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kImageHeight = 600
   * ```
   */
  private var fPointsPaint: SkPaint = TODO("Initialize fPointsPaint")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fDashPaint
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkScalar kMaxDashLen = 100;
   *         const SkScalar kDashGrowth = 1.2f;
   *
   *         STArray<100, SkScalar> intervals;
   *         for (SkScalar len = 1; len < kMaxDashLen; len *= kDashGrowth) {
   *             intervals.push_back(len);
   *             intervals.push_back(len);
   *         }
   *
   *         fDashPaint.setAntiAlias(true);
   *         fDashPaint.setStyle(SkPaint::kStroke_Style);
   *         fDashPaint.setStrokeWidth(6);
   *         fDashPaint.setColor(0xff008000);
   *         fDashPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0));
   *
   *         fPointsPaint.setColor(0xff800000);
   *         fPointsPaint.setStrokeWidth(3);
   *
   *         fRect = SkRect::MakeLTRB(10, 10, 100, 70);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("contour_start"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kImageWidth, kImageHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
   *             return SkPath::Rect(rect, dir, startIndex);
   *         });
   *
   *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
   *             return SkPath::Oval(rect, dir, startIndex);
   *         });
   *
   *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
   *             SkRRect rrect;
   *             const SkVector radii[4] = { {15, 15}, {15, 15}, {15, 15}, {15, 15}};
   *             rrect.setRectRadii(rect, radii);
   *             return SkPath::RRect(rrect, dir, startIndex);
   *         });
   *
   *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
   *             SkRRect rrect;
   *             rrect.setRect(rect);
   *             return SkPath::RRect(rrect, dir, startIndex);
   *         });
   *
   *         drawDirs(canvas, [](const SkRect& rect, SkPathDirection dir, unsigned startIndex) {
   *             SkRRect rrect;
   *             rrect.setOval(rect);
   *             return SkPath::RRect(rrect, dir, startIndex);
   *         });
   *
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawDirs(SkCanvas* canvas,
   *                   SkPath (*makePath)(const SkRect&, SkPathDirection, unsigned)) const {
   *         drawOneColumn(canvas, SkPathDirection::kCW, makePath);
   *         canvas->translate(kImageWidth / 10, 0);
   *         drawOneColumn(canvas, SkPathDirection::kCCW, makePath);
   *         canvas->translate(kImageWidth / 10, 0);
   *     }
   * ```
   */
  private fun drawDirs(canvas: SkCanvas?, param1: (
    Any,
    SkPathDirection,
    UInt,
  ) -> SkPath) {
    TODO("Implement drawDirs")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawOneColumn(SkCanvas* canvas, SkPathDirection dir,
   *                        SkPath (*makePath)(const SkRect&, SkPathDirection, unsigned)) const {
   *         SkAutoCanvasRestore acr(canvas, true);
   *
   *         for (unsigned i = 0; i < 8; ++i) {
   *             const SkPath path = makePath(fRect, dir, i);
   *             canvas->drawPath(path, fDashPaint);
   *             canvas->drawPoints(SkCanvas::kPoints_PointMode, path.points(), fPointsPaint);
   *
   *             canvas->translate(0, kImageHeight / 8);
   *         }
   *     }
   * ```
   */
  private fun drawOneColumn(
    canvas: SkCanvas?,
    dir: SkPathDirection,
    param2: (
      Any,
      SkPathDirection,
      UInt,
    ) -> SkPath,
  ) {
    TODO("Implement drawOneColumn")
  }

  public companion object {
    private val kImageWidth: Int = TODO("Initialize kImageWidth")

    private val kImageHeight: Int = TODO("Initialize kImageHeight")
  }
}
