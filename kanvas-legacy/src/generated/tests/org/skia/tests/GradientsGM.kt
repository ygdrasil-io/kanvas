package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsGM : public skiagm::GM {
 * public:
 *     GradientsGM(bool dither) : fDither(dither) {}
 *
 * protected:
 *     const bool fDither;
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPoint pts[2] = {
 *             { 0, 0 },
 *             { SkIntToScalar(100), SkIntToScalar(100) }
 *         };
 *         SkTileMode tm = SkTileMode::kClamp;
 *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         for (size_t i = 0; i < std::size(gGradData); i++) {
 *             canvas->save();
 *             for (size_t j = 0; j < std::size(gGradMakers); j++) {
 *                 SkMatrix scale = SkMatrix::I();
 *
 *                 if (i == 5) { // if the clamp case
 *                     scale.setScale(0.5f, 0.5f);
 *                     scale.postTranslate(25.f, 25.f);
 *                 }
 *
 *                 paint.setShader(gGradMakers[j](pts, gGradData[i], tm, scale));
 *                 canvas->drawRect(r, paint);
 *                 canvas->translate(0, SkIntToScalar(120));
 *             }
 *             canvas->restore();
 *             canvas->translate(SkIntToScalar(120), 0);
 *         }
 *     }
 *
 * private:
 *     void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 *
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients" : "gradients_nodither");
 *     }
 *
 *     SkISize getISize() override { return {840, 815}; }
 * }
 * ```
 */
public open class GradientsGM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const bool fDither
   * ```
   */
  protected val fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPoint pts[2] = {
   *             { 0, 0 },
   *             { SkIntToScalar(100), SkIntToScalar(100) }
   *         };
   *         SkTileMode tm = SkTileMode::kClamp;
   *         SkRect r = { 0, 0, SkIntToScalar(100), SkIntToScalar(100) };
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         for (size_t i = 0; i < std::size(gGradData); i++) {
   *             canvas->save();
   *             for (size_t j = 0; j < std::size(gGradMakers); j++) {
   *                 SkMatrix scale = SkMatrix::I();
   *
   *                 if (i == 5) { // if the clamp case
   *                     scale.setScale(0.5f, 0.5f);
   *                     scale.postTranslate(25.f, 25.f);
   *                 }
   *
   *                 paint.setShader(gGradMakers[j](pts, gGradData[i], tm, scale));
   *                 canvas->drawRect(r, paint);
   *                 canvas->translate(0, SkIntToScalar(120));
   *             }
   *             canvas->restore();
   *             canvas->translate(SkIntToScalar(120), 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString(fDither ? "gradients" : "gradients_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {840, 815}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }
}

public typealias GradientsViewPerspectiveGMINHERITED = GradientsGM
