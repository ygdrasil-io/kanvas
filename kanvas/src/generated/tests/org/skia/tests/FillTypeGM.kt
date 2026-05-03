package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkPathFillType
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class FillTypeGM : public GM {
 *     SkPath fPath;
 * public:
 *     FillTypeGM() {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 *     void makePath() {
 *         if (fPath.isEmpty()) {
 *             const SkScalar radius = SkIntToScalar(45);
 *             fPath = SkPathBuilder().addCircle(SkIntToScalar(50), SkIntToScalar(50), radius)
 *                                    .addCircle(SkIntToScalar(100), SkIntToScalar(100), radius)
 *                                    .detach();
 *         }
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("filltypes"); }
 *
 *     SkISize getISize() override { return SkISize::Make(835, 840); }
 *
 *     void showPath(SkCanvas* canvas, int x, int y, SkPathFillType ft,
 *                   SkScalar scale, const SkPaint& paint) {
 *         const SkRect r = { 0, 0, SkIntToScalar(150), SkIntToScalar(150) };
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
 *         canvas->clipRect(r);
 *         canvas->drawColor(SK_ColorWHITE);
 *         fPath.setFillType(ft);
 *         canvas->translate(r.centerX(), r.centerY());
 *         canvas->scale(scale, scale);
 *         canvas->translate(-r.centerX(), -r.centerY());
 *         canvas->drawPath(fPath, paint);
 *         canvas->restore();
 *     }
 *
 *     void showFour(SkCanvas* canvas, SkScalar scale, const SkPaint& paint) {
 *         showPath(canvas,   0,   0, SkPathFillType::kWinding,
 *                  scale, paint);
 *         showPath(canvas, 200,   0, SkPathFillType::kEvenOdd,
 *                  scale, paint);
 *         showPath(canvas,  00, 200, SkPathFillType::kInverseWinding,
 *                  scale, paint);
 *         showPath(canvas, 200, 200, SkPathFillType::kInverseEvenOdd,
 *                  scale, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->makePath();
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *
 *         SkPaint paint;
 *         const SkScalar scale = SkIntToScalar(5)/4;
 *
 *         paint.setAntiAlias(false);
 *
 *         showFour(canvas, SK_Scalar1, paint);
 *         canvas->translate(SkIntToScalar(450), 0);
 *         showFour(canvas, scale, paint);
 *
 *         paint.setAntiAlias(true);
 *
 *         canvas->translate(SkIntToScalar(-450), SkIntToScalar(450));
 *         showFour(canvas, SK_Scalar1, paint);
 *         canvas->translate(SkIntToScalar(450), 0);
 *         showFour(canvas, scale, paint);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FillTypeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * void makePath() {
   *         if (fPath.isEmpty()) {
   *             const SkScalar radius = SkIntToScalar(45);
   *             fPath = SkPathBuilder().addCircle(SkIntToScalar(50), SkIntToScalar(50), radius)
   *                                    .addCircle(SkIntToScalar(100), SkIntToScalar(100), radius)
   *                                    .detach();
   *         }
   *     }
   * ```
   */
  public fun makePath() {
    TODO("Implement makePath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("filltypes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(835, 840); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void showPath(SkCanvas* canvas, int x, int y, SkPathFillType ft,
   *                   SkScalar scale, const SkPaint& paint) {
   *         const SkRect r = { 0, 0, SkIntToScalar(150), SkIntToScalar(150) };
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
   *         canvas->clipRect(r);
   *         canvas->drawColor(SK_ColorWHITE);
   *         fPath.setFillType(ft);
   *         canvas->translate(r.centerX(), r.centerY());
   *         canvas->scale(scale, scale);
   *         canvas->translate(-r.centerX(), -r.centerY());
   *         canvas->drawPath(fPath, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun showPath(
    canvas: SkCanvas?,
    x: Int,
    y: Int,
    ft: SkPathFillType,
    scale: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement showPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void showFour(SkCanvas* canvas, SkScalar scale, const SkPaint& paint) {
   *         showPath(canvas,   0,   0, SkPathFillType::kWinding,
   *                  scale, paint);
   *         showPath(canvas, 200,   0, SkPathFillType::kEvenOdd,
   *                  scale, paint);
   *         showPath(canvas,  00, 200, SkPathFillType::kInverseWinding,
   *                  scale, paint);
   *         showPath(canvas, 200, 200, SkPathFillType::kInverseEvenOdd,
   *                  scale, paint);
   *     }
   * ```
   */
  protected fun showFour(
    canvas: SkCanvas?,
    scale: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement showFour")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->makePath();
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *
   *         SkPaint paint;
   *         const SkScalar scale = SkIntToScalar(5)/4;
   *
   *         paint.setAntiAlias(false);
   *
   *         showFour(canvas, SK_Scalar1, paint);
   *         canvas->translate(SkIntToScalar(450), 0);
   *         showFour(canvas, scale, paint);
   *
   *         paint.setAntiAlias(true);
   *
   *         canvas->translate(SkIntToScalar(-450), SkIntToScalar(450));
   *         showFour(canvas, SK_Scalar1, paint);
   *         canvas->translate(SkIntToScalar(450), 0);
   *         showFour(canvas, scale, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
