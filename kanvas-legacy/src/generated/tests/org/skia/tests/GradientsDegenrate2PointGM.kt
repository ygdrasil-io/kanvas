package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsDegenrate2PointGM : public skiagm::GM {
 * public:
 *     GradientsDegenrate2PointGM(bool dither) : fDither(dither) {}
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients_degenerate_2pt" : "gradients_degenerate_2pt_nodither");
 *     }
 *
 *     SkISize getISize() override { return {320, 320}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(SK_ColorBLUE);
 *
 *         SkColor4f colors[] = { SkColors::kRed, SkColors::kGreen, SkColors::kGreen, SkColors::kRed };
 *         SkScalar pos[] = { 0, 0.01f, 0.99f, SK_Scalar1 };
 *         SkPoint c0;
 *         c0.iset(-80, 25);
 *         SkScalar r0 = SkIntToScalar(70);
 *         SkPoint c1;
 *         c1.iset(0, 25);
 *         SkScalar r1 = SkIntToScalar(150);
 *         SkPaint paint;
 *         paint.setShader(SkShaders::TwoPointConicalGradient(c0, r0, c1, r1,
 *                                                        {{colors, pos, SkTileMode::kClamp}, {}}));
 *         paint.setDither(fDither);
 *         canvas->drawPaint(paint);
 *     }
 *
 *     bool fDither;
 * }
 * ```
 */
public open class GradientsDegenrate2PointGM public constructor(
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
   *         return SkString(fDither ? "gradients_degenerate_2pt" : "gradients_degenerate_2pt_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {320, 320}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawColor(SK_ColorBLUE);
   *
   *         SkColor4f colors[] = { SkColors::kRed, SkColors::kGreen, SkColors::kGreen, SkColors::kRed };
   *         SkScalar pos[] = { 0, 0.01f, 0.99f, SK_Scalar1 };
   *         SkPoint c0;
   *         c0.iset(-80, 25);
   *         SkScalar r0 = SkIntToScalar(70);
   *         SkPoint c1;
   *         c1.iset(0, 25);
   *         SkScalar r1 = SkIntToScalar(150);
   *         SkPaint paint;
   *         paint.setShader(SkShaders::TwoPointConicalGradient(c0, r0, c1, r1,
   *                                                        {{colors, pos, SkTileMode::kClamp}, {}}));
   *         paint.setDither(fDither);
   *         canvas->drawPaint(paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
