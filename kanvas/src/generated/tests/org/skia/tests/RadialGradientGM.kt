package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RadialGradientGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("radial_gradient"); }
 *
 *     SkISize getISize() override { return {1280, 1280}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkISize dim = this->getISize();
 *
 *         canvas->drawColor(0xFF000000);
 *
 *         SkPaint paint;
 *         paint.setDither(true);
 *         SkPoint center;
 *         center.set(SkIntToScalar(dim.width())/2, SkIntToScalar(dim.height())/2);
 *         SkScalar radius = SkIntToScalar(dim.width())/2;
 *         const SkScalar pos[] = { 0.0f,
 *                              0.35f,
 *                              1.0f };
 *         SkColorConverter conv({ 0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000 });
 *         paint.setShader(SkShaders::RadialGradient(center, radius,
 *                                               {{conv.colors4f(), pos, SkTileMode::kClamp}, {}}));
 *         SkRect r = {
 *             0, 0, SkIntToScalar(dim.width()), SkIntToScalar(dim.height())
 *         };
 *         canvas->drawRect(r, paint);
 *     }
 * }
 * ```
 */
public open class RadialGradientGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("radial_gradient"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1280, 1280}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkISize dim = this->getISize();
   *
   *         canvas->drawColor(0xFF000000);
   *
   *         SkPaint paint;
   *         paint.setDither(true);
   *         SkPoint center;
   *         center.set(SkIntToScalar(dim.width())/2, SkIntToScalar(dim.height())/2);
   *         SkScalar radius = SkIntToScalar(dim.width())/2;
   *         const SkScalar pos[] = { 0.0f,
   *                              0.35f,
   *                              1.0f };
   *         SkColorConverter conv({ 0x7f7f7f7f, 0x7f7f7f7f, 0xb2000000 });
   *         paint.setShader(SkShaders::RadialGradient(center, radius,
   *                                               {{conv.colors4f(), pos, SkTileMode::kClamp}, {}}));
   *         SkRect r = {
   *             0, 0, SkIntToScalar(dim.width()), SkIntToScalar(dim.height())
   *         };
   *         canvas->drawRect(r, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
