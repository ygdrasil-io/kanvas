package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComposeShaderAlphaGM : public skiagm::GM {
 * public:
 *     ComposeShaderAlphaGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("composeshader_alpha"); }
 *
 *     SkISize getISize() override { return SkISize::Make(750, 220); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkShader> shaders[] = {
 *             make_shader(SkBlendMode::kDstIn),
 *             make_shader(SkBlendMode::kSrcOver),
 *         };
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *
 *         const SkRect r = SkRect::MakeXYWH(5, 5, 100, 100);
 *
 *         for (size_t y = 0; y < std::size(shaders); ++y) {
 *             canvas->save();
 *             for (int alpha = 0xFF; alpha > 0; alpha -= 0x28) {
 *                 paint.setAlphaf(1.0f);
 *                 paint.setShader(nullptr);
 *                 canvas->drawRect(r, paint);
 *
 *                 paint.setAlpha(alpha);
 *                 paint.setShader(shaders[y]);
 *                 canvas->drawRect(r, paint);
 *
 *                 canvas->translate(r.width() + 5, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height() + 5);
 *         }
 *     }
 *
 * private:
 *     typedef GM INHERITED ;
 * }
 * ```
 */
public open class ComposeShaderAlphaGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("composeshader_alpha"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(750, 220); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         sk_sp<SkShader> shaders[] = {
   *             make_shader(SkBlendMode::kDstIn),
   *             make_shader(SkBlendMode::kSrcOver),
   *         };
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorGREEN);
   *
   *         const SkRect r = SkRect::MakeXYWH(5, 5, 100, 100);
   *
   *         for (size_t y = 0; y < std::size(shaders); ++y) {
   *             canvas->save();
   *             for (int alpha = 0xFF; alpha > 0; alpha -= 0x28) {
   *                 paint.setAlphaf(1.0f);
   *                 paint.setShader(nullptr);
   *                 canvas->drawRect(r, paint);
   *
   *                 paint.setAlpha(alpha);
   *                 paint.setShader(shaders[y]);
   *                 canvas->drawRect(r, paint);
   *
   *                 canvas->translate(r.width() + 5, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height() + 5);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
