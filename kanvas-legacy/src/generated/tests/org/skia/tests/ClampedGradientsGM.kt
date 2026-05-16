package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ClampedGradientsGM : public skiagm::GM {
 * public:
 *     ClampedGradientsGM(bool dither) : fDither(dither) {}
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "clamped_gradients" : "clamped_gradients_nodither");
 *     }
 *
 *     SkISize getISize() override { return {640, 510}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(0xFFDDDDDD);
 *
 *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(300) };
 *         SkPaint paint;
 *         paint.setDither(fDither);
 *         paint.setAntiAlias(true);
 *
 *         SkPoint center;
 *         center.iset(0, 300);
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         paint.setShader(SkShaders::RadialGradient(
 *             center, 200, {{gColors, {}, SkTileMode::kClamp}, {}}));
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     bool fDither;
 * }
 * ```
 */
public open class ClampedGradientsGM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString(fDither ? "clamped_gradients" : "clamped_gradients_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 510}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawColor(0xFFDDDDDD);
   *
   *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(300) };
   *         SkPaint paint;
   *         paint.setDither(fDither);
   *         paint.setAntiAlias(true);
   *
   *         SkPoint center;
   *         center.iset(0, 300);
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         paint.setShader(SkShaders::RadialGradient(
   *             center, 200, {{gColors, {}, SkTileMode::kClamp}, {}}));
   *         canvas->drawRect(r, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
