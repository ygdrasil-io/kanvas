package org.skia.tests

import kotlin.Boolean
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
 * class FillTypePerspGM : public GM {
 *     SkPath fPath;
 * public:
 *     FillTypePerspGM() {}
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
 *     SkString getName() const override { return SkString("filltypespersp"); }
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
 *     void showFour(SkCanvas* canvas, SkScalar scale, bool aa) {
 *         SkPaint paint;
 *         SkPoint center = SkPoint::Make(SkIntToScalar(100), SkIntToScalar(100));
 *         SkColor4f colors[] = {SkColors::kBlue, SkColors::kRed, SkColors::kGreen};
 *         SkScalar pos[] = {0, SK_ScalarHalf, SK_Scalar1};
 *         paint.setShader(SkShaders::RadialGradient(center, 100,
 *                                                   {{colors, pos, SkTileMode::kClamp}, {}}));
 *         paint.setAntiAlias(aa);
 *
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
 *         // do perspective drawPaint as the background;
 *         SkPaint bkgnrd;
 *         SkPoint center = SkPoint::Make(SkIntToScalar(100),
 *                                        SkIntToScalar(100));
 *         SkColor4f colors[] = {SkColors::kBlack, SkColors::kCyan,
 *                             SkColors::kYellow, SkColors::kWhite};
 *         SkScalar pos[] = {0, SK_ScalarHalf / 2,
 *                           3 * SK_ScalarHalf / 2, SK_Scalar1};
 *         bkgnrd.setShader(SkShaders::RadialGradient(center, 1000,
 *                                                    {{colors, pos, SkTileMode::kClamp}, {}}));
 *         canvas->save();
 *             canvas->translate(SkIntToScalar(100), SkIntToScalar(100));
 *             SkMatrix mat;
 *             mat.reset();
 *             mat.setPerspY(SK_Scalar1 / 1000);
 *             canvas->concat(mat);
 *             canvas->drawPaint(bkgnrd);
 *         canvas->restore();
 *
 *         // draw the paths in perspective
 *         SkMatrix persp;
 *         persp.reset();
 *         persp.setPerspX(-SK_Scalar1 / 1800);
 *         persp.setPerspY(SK_Scalar1 / 500);
 *         canvas->concat(persp);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         const SkScalar scale = SkIntToScalar(5)/4;
 *
 *         showFour(canvas, SK_Scalar1, false);
 *         canvas->translate(SkIntToScalar(450), 0);
 *         showFour(canvas, scale, false);
 *
 *         canvas->translate(SkIntToScalar(-450), SkIntToScalar(450));
 *         showFour(canvas, SK_Scalar1, true);
 *         canvas->translate(SkIntToScalar(450), 0);
 *         showFour(canvas, scale, true);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FillTypePerspGM public constructor() : GM() {
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
   * SkString getName() const override { return SkString("filltypespersp"); }
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
   * void showFour(SkCanvas* canvas, SkScalar scale, bool aa) {
   *         SkPaint paint;
   *         SkPoint center = SkPoint::Make(SkIntToScalar(100), SkIntToScalar(100));
   *         SkColor4f colors[] = {SkColors::kBlue, SkColors::kRed, SkColors::kGreen};
   *         SkScalar pos[] = {0, SK_ScalarHalf, SK_Scalar1};
   *         paint.setShader(SkShaders::RadialGradient(center, 100,
   *                                                   {{colors, pos, SkTileMode::kClamp}, {}}));
   *         paint.setAntiAlias(aa);
   *
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
    aa: Boolean,
  ) {
    TODO("Implement showFour")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->makePath();
   *
   *         // do perspective drawPaint as the background;
   *         SkPaint bkgnrd;
   *         SkPoint center = SkPoint::Make(SkIntToScalar(100),
   *                                        SkIntToScalar(100));
   *         SkColor4f colors[] = {SkColors::kBlack, SkColors::kCyan,
   *                             SkColors::kYellow, SkColors::kWhite};
   *         SkScalar pos[] = {0, SK_ScalarHalf / 2,
   *                           3 * SK_ScalarHalf / 2, SK_Scalar1};
   *         bkgnrd.setShader(SkShaders::RadialGradient(center, 1000,
   *                                                    {{colors, pos, SkTileMode::kClamp}, {}}));
   *         canvas->save();
   *             canvas->translate(SkIntToScalar(100), SkIntToScalar(100));
   *             SkMatrix mat;
   *             mat.reset();
   *             mat.setPerspY(SK_Scalar1 / 1000);
   *             canvas->concat(mat);
   *             canvas->drawPaint(bkgnrd);
   *         canvas->restore();
   *
   *         // draw the paths in perspective
   *         SkMatrix persp;
   *         persp.reset();
   *         persp.setPerspX(-SK_Scalar1 / 1800);
   *         persp.setPerspY(SK_Scalar1 / 500);
   *         canvas->concat(persp);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         const SkScalar scale = SkIntToScalar(5)/4;
   *
   *         showFour(canvas, SK_Scalar1, false);
   *         canvas->translate(SkIntToScalar(450), 0);
   *         showFour(canvas, scale, false);
   *
   *         canvas->translate(SkIntToScalar(-450), SkIntToScalar(450));
   *         showFour(canvas, SK_Scalar1, true);
   *         canvas->translate(SkIntToScalar(450), 0);
   *         showFour(canvas, scale, true);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
