package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ChromeGradTextGM1 : public skiagm::GM {
 *     SkString getName() const override { return SkString("chrome_gradtext1"); }
 *
 *     SkISize getISize() override { return {500, 480}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(100), SkIntToScalar(100));
 *
 *         canvas->clipRect(r);
 *
 *         paint.setColor(SkColors::kRed);
 *         canvas->drawRect(r, paint);
 *
 *         // Minimal repro doesn't require AA, LCD, or a nondefault typeface
 *         paint.setShader(make_chrome_solid());
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 500);
 *         font.setEdging(SkFont::Edging::kAlias);
 *
 *         canvas->drawString("I", 0, 100, font, paint);
 *     }
 * }
 * ```
 */
public open class ChromeGradTextGM1 : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("chrome_gradtext1"); }
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
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(100), SkIntToScalar(100));
   *
   *         canvas->clipRect(r);
   *
   *         paint.setColor(SkColors::kRed);
   *         canvas->drawRect(r, paint);
   *
   *         // Minimal repro doesn't require AA, LCD, or a nondefault typeface
   *         paint.setShader(make_chrome_solid());
   *
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 500);
   *         font.setEdging(SkFont::Edging::kAlias);
   *
   *         canvas->drawString("I", 0, 100, font, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
