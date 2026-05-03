package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BeziersGM : public skiagm::GM {
 * public:
 *     BeziersGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("beziers"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(SkIntToScalar(9)/2);
 *         paint.setAntiAlias(true);
 *
 *         SkRandom rand;
 *         for (int i = 0; i < N; i++) {
 *             canvas->drawPath(rnd_quad(&paint, rand), paint);
 *         }
 *         canvas->translate(0, SH);
 *         for (int i = 0; i < N; i++) {
 *             canvas->drawPath(rnd_cubic(&paint, rand), paint);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BeziersGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("beziers"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(SkIntToScalar(9)/2);
   *         paint.setAntiAlias(true);
   *
   *         SkRandom rand;
   *         for (int i = 0; i < N; i++) {
   *             canvas->drawPath(rnd_quad(&paint, rand), paint);
   *         }
   *         canvas->translate(0, SH);
   *         for (int i = 0; i < N; i++) {
   *             canvas->drawPath(rnd_cubic(&paint, rand), paint);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
