package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LcdTextSizeGM : public skiagm::GM {
 *     static void ScaleAbout(SkCanvas* canvas, SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
 *         SkMatrix m;
 *         m.setScale(sx, sy, px, py);
 *         canvas->concat(m);
 *     }
 *
 *     SkString getName() const override { return SkString("lcdtextsize"); }
 *
 *     SkISize getISize() override { return {320, 120}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const char* lcd_text = "LCD";
 *         const char* gray_text = "GRAY";
 *
 *         constexpr static float kLCDTextSizeLimit = 48;
 *
 *         const struct {
 *             SkPoint     fLoc;
 *             SkScalar    fTextSize;
 *             SkScalar    fScale;
 *             const char* fText;
 *         } rec[] = {
 *             { {  10,  50 }, kLCDTextSizeLimit - 1,     1,  lcd_text },
 *             { { 160,  50 }, kLCDTextSizeLimit + 1,     1,  gray_text },
 *             { {  10, 100 }, kLCDTextSizeLimit / 2, 1.99f,  lcd_text },
 *             { { 160, 100 }, kLCDTextSizeLimit / 2, 2.01f,  gray_text },
 *         };
 *
 *         for (size_t i = 0; i < std::size(rec); ++i) {
 *             const SkPoint loc = rec[i].fLoc;
 *             SkAutoCanvasRestore acr(canvas, true);
 *
 *             SkFont font(ToolUtils::DefaultPortableTypeface(), rec[i].fTextSize);
 *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *             ScaleAbout(canvas, rec[i].fScale, rec[i].fScale, loc.x(), loc.y());
 *             canvas->drawString(rec[i].fText, loc.x(), loc.y(), font, SkPaint());
 *         }
 *     }
 * }
 * ```
 */
public open class LcdTextSizeGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lcdtextsize"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {320, 120}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const char* lcd_text = "LCD";
   *         const char* gray_text = "GRAY";
   *
   *         constexpr static float kLCDTextSizeLimit = 48;
   *
   *         const struct {
   *             SkPoint     fLoc;
   *             SkScalar    fTextSize;
   *             SkScalar    fScale;
   *             const char* fText;
   *         } rec[] = {
   *             { {  10,  50 }, kLCDTextSizeLimit - 1,     1,  lcd_text },
   *             { { 160,  50 }, kLCDTextSizeLimit + 1,     1,  gray_text },
   *             { {  10, 100 }, kLCDTextSizeLimit / 2, 1.99f,  lcd_text },
   *             { { 160, 100 }, kLCDTextSizeLimit / 2, 2.01f,  gray_text },
   *         };
   *
   *         for (size_t i = 0; i < std::size(rec); ++i) {
   *             const SkPoint loc = rec[i].fLoc;
   *             SkAutoCanvasRestore acr(canvas, true);
   *
   *             SkFont font(ToolUtils::DefaultPortableTypeface(), rec[i].fTextSize);
   *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *             ScaleAbout(canvas, rec[i].fScale, rec[i].fScale, loc.x(), loc.y());
   *             canvas->drawString(rec[i].fText, loc.x(), loc.y(), font, SkPaint());
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void ScaleAbout(SkCanvas* canvas, SkScalar sx, SkScalar sy, SkScalar px, SkScalar py) {
     *         SkMatrix m;
     *         m.setScale(sx, sy, px, py);
     *         canvas->concat(m);
     *     }
     * ```
     */
    private fun scaleAbout(
      canvas: SkCanvas?,
      sx: SkScalar,
      sy: SkScalar,
      px: SkScalar,
      py: SkScalar,
    ) {
      TODO("Implement scaleAbout")
    }
  }
}
