package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsNoTextureGM : public GM {
 * public:
 *     GradientsNoTextureGM(bool dither) : fDither(dither) {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients_no_texture" : "gradients_no_texture_nodither");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 615); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr SkPoint kPts[2] = { { 0, 0 },
 *                                          { SkIntToScalar(50), SkIntToScalar(50) } };
 *         constexpr SkTileMode kTM = SkTileMode::kClamp;
 *         SkRect kRect = { 0, 0, SkIntToScalar(50), SkIntToScalar(50) };
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setDither(fDither);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         constexpr uint8_t kAlphas[] = { 0xff, 0x40 };
 *         for (size_t a = 0; a < std::size(kAlphas); ++a) {
 *             for (size_t i = 0; i < std::size(gGradData); ++i) {
 *                 canvas->save();
 *                 for (size_t j = 0; j < std::size(gGradMakers); ++j) {
 *                     paint.setShader(gGradMakers[j](kPts, gGradData[i], kTM));
 *                     paint.setAlpha(kAlphas[a]);
 *                     canvas->drawRect(kRect, paint);
 *                     canvas->translate(0, SkIntToScalar(kRect.height() + 20));
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(SkIntToScalar(kRect.width() + 20), 0);
 *             }
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
public open class GradientsNoTextureGM public constructor(
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
   *         return SkString(fDither ? "gradients_no_texture" : "gradients_no_texture_nodither");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 615); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr SkPoint kPts[2] = { { 0, 0 },
   *                                          { SkIntToScalar(50), SkIntToScalar(50) } };
   *         constexpr SkTileMode kTM = SkTileMode::kClamp;
   *         SkRect kRect = { 0, 0, SkIntToScalar(50), SkIntToScalar(50) };
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setDither(fDither);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         constexpr uint8_t kAlphas[] = { 0xff, 0x40 };
   *         for (size_t a = 0; a < std::size(kAlphas); ++a) {
   *             for (size_t i = 0; i < std::size(gGradData); ++i) {
   *                 canvas->save();
   *                 for (size_t j = 0; j < std::size(gGradMakers); ++j) {
   *                     paint.setShader(gGradMakers[j](kPts, gGradData[i], kTM));
   *                     paint.setAlpha(kAlphas[a]);
   *                     canvas->drawRect(kRect, paint);
   *                     canvas->translate(0, SkIntToScalar(kRect.height() + 20));
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(SkIntToScalar(kRect.width() + 20), 0);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
