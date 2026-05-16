package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FontScalerGM : public GM {
 * public:
 *     FontScalerGM() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("fontscaler"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1450, 750); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         //With freetype the default (normal hinting) can be really ugly.
 *         //Most distros now set slight (vertical hinting only) in any event.
 *         font.setHinting(SkFontHinting::kSlight);
 *
 *         const char* text = "Hamburgefons ooo mmm";
 *         const size_t textLen = strlen(text);
 *
 *         for (int j = 0; j < 2; ++j) {
 *             // This used to do 6 iterations but it causes the N4 to crash in the MSAA4 config.
 *             for (int i = 0; i < 5; ++i) {
 *                 SkScalar x = 10;
 *                 SkScalar y = 20;
 *
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->translate(SkIntToScalar(50 + i * 230),
 *                                   SkIntToScalar(20));
 *                 canvas->rotate(SkIntToScalar(i * 5), x, y * 10);
 *
 *                 {
 *                     SkPaint p;
 *                     p.setAntiAlias(true);
 *                     SkRect r;
 *                     r.setLTRB(x - 3, 15, x - 1, 280);
 *                     canvas->drawRect(r, p);
 *                 }
 *
 *                 for (int ps = 6; ps <= 22; ps++) {
 *                     font.setSize(SkIntToScalar(ps));
 *                     canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, SkPaint());
 *                     y += font.getMetrics(nullptr);
 *                 }
 *             }
 *             canvas->translate(0, SkIntToScalar(360));
 *             font.setSubpixel(true);
 *             font.setLinearMetrics(true);
 *             font.setBaselineSnap(false);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class FontScalerGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fontscaler"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1450, 750); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         //With freetype the default (normal hinting) can be really ugly.
   *         //Most distros now set slight (vertical hinting only) in any event.
   *         font.setHinting(SkFontHinting::kSlight);
   *
   *         const char* text = "Hamburgefons ooo mmm";
   *         const size_t textLen = strlen(text);
   *
   *         for (int j = 0; j < 2; ++j) {
   *             // This used to do 6 iterations but it causes the N4 to crash in the MSAA4 config.
   *             for (int i = 0; i < 5; ++i) {
   *                 SkScalar x = 10;
   *                 SkScalar y = 20;
   *
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->translate(SkIntToScalar(50 + i * 230),
   *                                   SkIntToScalar(20));
   *                 canvas->rotate(SkIntToScalar(i * 5), x, y * 10);
   *
   *                 {
   *                     SkPaint p;
   *                     p.setAntiAlias(true);
   *                     SkRect r;
   *                     r.setLTRB(x - 3, 15, x - 1, 280);
   *                     canvas->drawRect(r, p);
   *                 }
   *
   *                 for (int ps = 6; ps <= 22; ps++) {
   *                     font.setSize(SkIntToScalar(ps));
   *                     canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, SkPaint());
   *                     y += font.getMetrics(nullptr);
   *                 }
   *             }
   *             canvas->translate(0, SkIntToScalar(360));
   *             font.setSubpixel(true);
   *             font.setLinearMetrics(true);
   *             font.setBaselineSnap(false);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
