package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RadialGradient4GM : public skiagm::GM {
 * public:
 *     RadialGradient4GM(bool dither) : fDither(dither) { }
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "radial_gradient4" : "radial_gradient4_nodither");
 *     }
 *
 *     SkISize getISize() override { return {500, 500}; }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkPoint center = { 250, 250 };
 *         const SkScalar kRadius = 250;
 *         const SkColor4f colors[] = {
 *             SkColors::kRed, SkColors::kRed, SkColors::kWhite, SkColors::kWhite,SkColors::kRed };
 *         const SkScalar pos[] = { 0, .4f, .4f, .8f, .8f };
 *         fShader = SkShaders::RadialGradient(center, kRadius,
 *                                             {{colors, pos, SkTileMode::kClamp}, {}});
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *         paint.setShader(fShader);
 *         canvas->drawRect(SkRect::MakeWH(500, 500), paint);
 *     }
 *
 * private:
 *     sk_sp<SkShader> fShader;
 *     bool fDither;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class RadialGradient4GM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

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
   *         return SkString(fDither ? "radial_gradient4" : "radial_gradient4_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {500, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkPoint center = { 250, 250 };
   *         const SkScalar kRadius = 250;
   *         const SkColor4f colors[] = {
   *             SkColors::kRed, SkColors::kRed, SkColors::kWhite, SkColors::kWhite,SkColors::kRed };
   *         const SkScalar pos[] = { 0, .4f, .4f, .8f, .8f };
   *         fShader = SkShaders::RadialGradient(center, kRadius,
   *                                             {{colors, pos, SkTileMode::kClamp}, {}});
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *         paint.setShader(fShader);
   *         canvas->drawRect(SkRect::MakeWH(500, 500), paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
