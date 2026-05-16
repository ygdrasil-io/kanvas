package org.skia.tests

import kotlin.Array
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GammaShaderTextGM : public skiagm::GM {
 *     sk_sp<SkShader> fShaders[3];
 *     SkColor fColors[3];
 *
 * public:
 *     GammaShaderTextGM() {
 *         const SkColor colors[] = { SK_ColorBLACK, SK_ColorRED, SK_ColorBLUE };
 *         for (size_t i = 0; i < std::size(fShaders); ++i) {
 *             fColors[i] = colors[i];
 *         }
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("gammagradienttext"); }
 *
 *     SkISize getISize() override { return SkISize::Make(300, 300); }
 *
 *     void onOnceBeforeDraw() override {
 *         for (size_t i = 0; i < std::size(fShaders); ++i) {
 *             fShaders[i] = make_gradient(fColors[i]);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         sk_sp<SkTypeface> tf = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Italic());
 *         SkASSERT(tf);
 *         SkFont font(tf, 18);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         for (size_t i = 0; i < std::size(fShaders); ++i) {
 *             draw_pair(canvas, font, fColors[i], fShaders[i]);
 *             canvas->translate(0, 80);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class GammaShaderTextGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShaders[3]
   * ```
   */
  private var fShaders: Array<SkSp<SkShader>> = TODO("Initialize fShaders")

  /**
   * C++ original:
   * ```cpp
   * SkColor fColors[3]
   * ```
   */
  private var fColors: Array<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("gammagradienttext"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(300, 300); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (size_t i = 0; i < std::size(fShaders); ++i) {
   *             fShaders[i] = make_gradient(fColors[i]);
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         sk_sp<SkTypeface> tf = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Italic());
   *         SkASSERT(tf);
   *         SkFont font(tf, 18);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *         for (size_t i = 0; i < std::size(fShaders); ++i) {
   *             draw_pair(canvas, font, fColors[i], fShaders[i]);
   *             canvas->translate(0, 80);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
