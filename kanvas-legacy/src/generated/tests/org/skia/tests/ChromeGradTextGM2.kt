package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ChromeGradTextGM2 : public skiagm::GM {
 *     SkString getName() const override { return SkString("chrome_gradtext2"); }
 *
 *     SkISize getISize() override { return {500, 480}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         SkFont  font = ToolUtils::DefaultPortableFont();
 *         font.setEdging(SkFont::Edging::kAlias);
 *
 *         paint.setStyle(SkPaint::kFill_Style);
 *         canvas->drawString("Normal Fill Text", 0, 50, font, paint);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawString("Normal Stroke Text", 0, 100, font, paint);
 *
 *         // Minimal repro doesn't require AA, LCD, or a nondefault typeface
 *         paint.setShader(make_chrome_solid());
 *
 *         paint.setStyle(SkPaint::kFill_Style);
 *         canvas->drawString("Gradient Fill Text", 0, 150, font, paint);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawString("Gradient Stroke Text", 0, 200, font, paint);
 *     }
 * }
 * ```
 */
public open class ChromeGradTextGM2 : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("chrome_gradtext2"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {500, 480}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         SkFont  font = ToolUtils::DefaultPortableFont();
   *         font.setEdging(SkFont::Edging::kAlias);
   *
   *         paint.setStyle(SkPaint::kFill_Style);
   *         canvas->drawString("Normal Fill Text", 0, 50, font, paint);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         canvas->drawString("Normal Stroke Text", 0, 100, font, paint);
   *
   *         // Minimal repro doesn't require AA, LCD, or a nondefault typeface
   *         paint.setShader(make_chrome_solid());
   *
   *         paint.setStyle(SkPaint::kFill_Style);
   *         canvas->drawString("Gradient Fill Text", 0, 150, font, paint);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         canvas->drawString("Gradient Stroke Text", 0, 200, font, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
