package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class BmpFilterQualityRepeat : public skiagm::GM {
 * public:
 *     BmpFilterQualityRepeat() { this->setBGColor(ToolUtils::color_to_565(0xFFCCBBAA)); }
 *
 * protected:
 *
 *     void onOnceBeforeDraw() override {
 *         fBmp.allocN32Pixels(40, 40, true);
 *         SkCanvas canvas(fBmp);
 *         SkBitmap colorBmp;
 *         colorBmp.allocN32Pixels(20, 20, true);
 *         colorBmp.eraseColor(0xFFFF0000);
 *         canvas.drawImage(colorBmp.asImage(), 0, 0);
 *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFF008200));
 *         canvas.drawImage(colorBmp.asImage(), 20, 0);
 *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFFFF9000));
 *         canvas.drawImage(colorBmp.asImage(), 0, 20);
 *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFF2000FF));
 *         canvas.drawImage(colorBmp.asImage(), 20, 20);
 *     }
 *
 *     SkString getName() const override { return SkString("bmp_filter_quality_repeat"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1000, 400); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->drawAll(canvas, 2.5f);
 *         canvas->translate(0, 250);
 *         canvas->scale(0.5, 0.5);
 *         this->drawAll(canvas, 1);
 *     }
 *
 * private:
 *     void drawAll(SkCanvas* canvas, SkScalar scaleX) const {
 *         SkRect rect = SkRect::MakeLTRB(20, 60, 220, 210);
 *         SkMatrix lm = SkMatrix::I();
 *         lm.setScaleX(scaleX);
 *         lm.setTranslateX(423);
 *         lm.setTranslateY(330);
 *
 *         SkPaint textPaint;
 *         textPaint.setAntiAlias(true);
 *
 *         SkPaint bmpPaint(textPaint);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         SkAutoCanvasRestore acr(canvas, true);
 *
 *         const struct {
 *             const char* name;
 *             SkSamplingOptions sampling;
 *         } recs[] = {
 *             { "none",   SkSamplingOptions(SkFilterMode::kNearest) },
 *             { "low",    SkSamplingOptions(SkFilterMode::kLinear) },
 *             { "medium", SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear) },
 *             { "high",   SkSamplingOptions(SkCubicResampler::Mitchell()) },
 *         };
 *
 *         for (const auto& rec : recs) {
 *             constexpr SkTileMode kTM = SkTileMode::kRepeat;
 *             bmpPaint.setShader(fBmp.makeShader(kTM, kTM, rec.sampling, lm));
 *             canvas->drawRect(rect, bmpPaint);
 *             canvas->drawString(rec.name, 20, 40, font, textPaint);
 *             canvas->translate(250, 0);
 *         }
 *
 *     }
 *
 *     SkBitmap    fBmp;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BmpFilterQualityRepeat public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fBmp
   * ```
   */
  private var fBmp: SkBitmap = TODO("Initialize fBmp")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fBmp.allocN32Pixels(40, 40, true);
   *         SkCanvas canvas(fBmp);
   *         SkBitmap colorBmp;
   *         colorBmp.allocN32Pixels(20, 20, true);
   *         colorBmp.eraseColor(0xFFFF0000);
   *         canvas.drawImage(colorBmp.asImage(), 0, 0);
   *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFF008200));
   *         canvas.drawImage(colorBmp.asImage(), 20, 0);
   *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFFFF9000));
   *         canvas.drawImage(colorBmp.asImage(), 0, 20);
   *         colorBmp.eraseColor(ToolUtils::color_to_565(0xFF2000FF));
   *         canvas.drawImage(colorBmp.asImage(), 20, 20);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bmp_filter_quality_repeat"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1000, 400); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->drawAll(canvas, 2.5f);
   *         canvas->translate(0, 250);
   *         canvas->scale(0.5, 0.5);
   *         this->drawAll(canvas, 1);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawAll(SkCanvas* canvas, SkScalar scaleX) const {
   *         SkRect rect = SkRect::MakeLTRB(20, 60, 220, 210);
   *         SkMatrix lm = SkMatrix::I();
   *         lm.setScaleX(scaleX);
   *         lm.setTranslateX(423);
   *         lm.setTranslateY(330);
   *
   *         SkPaint textPaint;
   *         textPaint.setAntiAlias(true);
   *
   *         SkPaint bmpPaint(textPaint);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         SkAutoCanvasRestore acr(canvas, true);
   *
   *         const struct {
   *             const char* name;
   *             SkSamplingOptions sampling;
   *         } recs[] = {
   *             { "none",   SkSamplingOptions(SkFilterMode::kNearest) },
   *             { "low",    SkSamplingOptions(SkFilterMode::kLinear) },
   *             { "medium", SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear) },
   *             { "high",   SkSamplingOptions(SkCubicResampler::Mitchell()) },
   *         };
   *
   *         for (const auto& rec : recs) {
   *             constexpr SkTileMode kTM = SkTileMode::kRepeat;
   *             bmpPaint.setShader(fBmp.makeShader(kTM, kTM, rec.sampling, lm));
   *             canvas->drawRect(rect, bmpPaint);
   *             canvas->drawString(rec.name, 20, 40, font, textPaint);
   *             canvas->translate(250, 0);
   *         }
   *
   *     }
   * ```
   */
  private fun drawAll(canvas: SkCanvas?, scaleX: SkScalar) {
    TODO("Implement drawAll")
  }
}
