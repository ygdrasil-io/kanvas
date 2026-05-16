package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RadialGradient2GM : public skiagm::GM {
 * public:
 *     RadialGradient2GM(bool dither) : fDither(dither) {}
 *
 * private:
 *     SkString getName() const override {
 *         return SkString(fDither ? "radial_gradient2" : "radial_gradient2_nodither");
 *     }
 *
 *     SkISize getISize() override { return {800, 400}; }
 *
 *     // Reproduces the example given in b/7671058.
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint1, paint2, paint3;
 *         paint1.setStyle(SkPaint::kFill_Style);
 *         paint2.setStyle(SkPaint::kFill_Style);
 *         paint3.setStyle(SkPaint::kFill_Style);
 *
 *         const SkColor4f sweep_colors[] =
 *             { {1,0,0,1}, {1,1,0,1}, {0,1,0,1}, {0,1,1,1}, {0,0,1,1}, {1,0,1,1}, {1,0,0,1} };
 *         const SkColor4f colors1[] = { {1,1,1,1}, {0,0,0,0} };
 *         const SkColor4f colors2[] = { {0,0,0,1}, {0,0,0,0} };
 *
 *         const SkScalar cx = 200, cy = 200, radius = 150;
 *         SkPoint center;
 *         center.set(cx, cy);
 *
 *         // We can either interpolate endpoints and premultiply each point (default, more precision),
 *         // or premultiply the endpoints first, avoiding the need to premultiply each point (cheap).
 *         const SkGradient::Interpolation::InPremul flags[] = {
 *             SkGradient::Interpolation::InPremul::kNo,
 *             SkGradient::Interpolation::InPremul::kYes,
 *         };
 *         const SkTileMode tm = SkTileMode::kClamp;
 *
 *         for (size_t i = 0; i < std::size(flags); i++) {
 *             SkGradient::Interpolation terp{flags[i]};
 *             paint1.setShader(SkShaders::SweepGradient(center, {{sweep_colors, {}, tm}, terp}));
 *             paint2.setShader(SkShaders::RadialGradient(center, radius, {{colors1, {}, tm}, terp}));
 *             paint3.setShader(SkShaders::RadialGradient(center, radius, {{colors2, {}, tm}, terp}));
 *             paint1.setDither(fDither);
 *             paint2.setDither(fDither);
 *             paint3.setDither(fDither);
 *
 *             canvas->drawCircle(cx, cy, radius, paint1);
 *             canvas->drawCircle(cx, cy, radius, paint3);
 *             canvas->drawCircle(cx, cy, radius, paint2);
 *
 *             canvas->translate(400, 0);
 *         }
 *     }
 *
 * private:
 *     bool fDither;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class RadialGradient2GM public constructor(
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
   *         return SkString(fDither ? "radial_gradient2" : "radial_gradient2_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {800, 400}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint1, paint2, paint3;
   *         paint1.setStyle(SkPaint::kFill_Style);
   *         paint2.setStyle(SkPaint::kFill_Style);
   *         paint3.setStyle(SkPaint::kFill_Style);
   *
   *         const SkColor4f sweep_colors[] =
   *             { {1,0,0,1}, {1,1,0,1}, {0,1,0,1}, {0,1,1,1}, {0,0,1,1}, {1,0,1,1}, {1,0,0,1} };
   *         const SkColor4f colors1[] = { {1,1,1,1}, {0,0,0,0} };
   *         const SkColor4f colors2[] = { {0,0,0,1}, {0,0,0,0} };
   *
   *         const SkScalar cx = 200, cy = 200, radius = 150;
   *         SkPoint center;
   *         center.set(cx, cy);
   *
   *         // We can either interpolate endpoints and premultiply each point (default, more precision),
   *         // or premultiply the endpoints first, avoiding the need to premultiply each point (cheap).
   *         const SkGradient::Interpolation::InPremul flags[] = {
   *             SkGradient::Interpolation::InPremul::kNo,
   *             SkGradient::Interpolation::InPremul::kYes,
   *         };
   *         const SkTileMode tm = SkTileMode::kClamp;
   *
   *         for (size_t i = 0; i < std::size(flags); i++) {
   *             SkGradient::Interpolation terp{flags[i]};
   *             paint1.setShader(SkShaders::SweepGradient(center, {{sweep_colors, {}, tm}, terp}));
   *             paint2.setShader(SkShaders::RadialGradient(center, radius, {{colors1, {}, tm}, terp}));
   *             paint3.setShader(SkShaders::RadialGradient(center, radius, {{colors2, {}, tm}, terp}));
   *             paint1.setDither(fDither);
   *             paint2.setDither(fDither);
   *             paint3.setDither(fDither);
   *
   *             canvas->drawCircle(cx, cy, radius, paint1);
   *             canvas->drawCircle(cx, cy, radius, paint3);
   *             canvas->drawCircle(cx, cy, radius, paint2);
   *
   *             canvas->translate(400, 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
