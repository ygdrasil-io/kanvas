package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.tools.EmojiFontFormat
import org.skia.tools.EmojiTestSample

/**
 * C++ original:
 * ```cpp
 * class ScaledEmojiGM : public GM {
 * public:
 *     ScaledEmojiGM(ToolUtils::EmojiFontFormat format) : fFormat(format) {}
 *
 * protected:
 *     ToolUtils::EmojiTestSample fEmojiFont;
 *
 *     void onOnceBeforeDraw() override { fEmojiFont = ToolUtils::EmojiSample(fFormat); }
 *
 *     SkString getName() const override {
 *         return SkString("scaledemoji_") += ToolUtils::NameForFontFormat(fFormat);
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(1200, 1200); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!fEmojiFont.typeface) {
 *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
 *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
 *             return DrawResult::kSkip;
 *         }
 *
 *         canvas->drawColor(SK_ColorGRAY);
 *
 *         SkPaint paint;
 *         SkFont font(fEmojiFont.typeface);
 *         font.setEdging(SkFont::Edging::kAlias);
 *
 *         const char* text = fEmojiFont.sampleText;
 *
 *         // draw text at different point sizes
 *         // Testing GPU bitmap path, SDF path with no scaling,
 *         // SDF path with scaling, path rendering with scaling
 *         SkFontMetrics metrics;
 *         SkScalar y = 0;
 *         for (SkScalar textSize : {70, 180, 270, 340}) {
 *             font.setSize(textSize);
 *             font.getMetrics(&metrics);
 *             y += -metrics.fAscent;
 *             canvas->drawSimpleText(text, strlen(text), SkTextEncoding::kUTF8, 10, y, font, paint);
 *             y += metrics.fDescent + metrics.fLeading;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     ToolUtils::EmojiFontFormat fFormat;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ScaledEmojiGM public constructor(
  format: EmojiFontFormat,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * ToolUtils::EmojiTestSample fEmojiFont
   * ```
   */
  protected var fEmojiFont: EmojiTestSample = TODO("Initialize fEmojiFont")

  /**
   * C++ original:
   * ```cpp
   * ToolUtils::EmojiFontFormat fFormat
   * ```
   */
  private var fFormat: EmojiFontFormat = TODO("Initialize fFormat")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { fEmojiFont = ToolUtils::EmojiSample(fFormat); }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkString("scaledemoji_") += ToolUtils::NameForFontFormat(fFormat);
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1200, 1200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!fEmojiFont.typeface) {
   *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
   *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
   *             return DrawResult::kSkip;
   *         }
   *
   *         canvas->drawColor(SK_ColorGRAY);
   *
   *         SkPaint paint;
   *         SkFont font(fEmojiFont.typeface);
   *         font.setEdging(SkFont::Edging::kAlias);
   *
   *         const char* text = fEmojiFont.sampleText;
   *
   *         // draw text at different point sizes
   *         // Testing GPU bitmap path, SDF path with no scaling,
   *         // SDF path with scaling, path rendering with scaling
   *         SkFontMetrics metrics;
   *         SkScalar y = 0;
   *         for (SkScalar textSize : {70, 180, 270, 340}) {
   *             font.setSize(textSize);
   *             font.getMetrics(&metrics);
   *             y += -metrics.fAscent;
   *             canvas->drawSimpleText(text, strlen(text), SkTextEncoding::kUTF8, 10, y, font, paint);
   *             y += metrics.fDescent + metrics.fLeading;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
