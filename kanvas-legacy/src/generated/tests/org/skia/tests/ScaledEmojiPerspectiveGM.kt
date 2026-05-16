package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.tools.EmojiFontFormat
import org.skia.tools.EmojiTestSample

/**
 * C++ original:
 * ```cpp
 * class ScaledEmojiPerspectiveGM : public GM {
 * public:
 *     ScaledEmojiPerspectiveGM(ToolUtils::EmojiFontFormat format) : fFormat(format) {}
 *
 * protected:
 *     ToolUtils::EmojiTestSample fEmojiFont;
 *     SkString fStripSpacesSampleText;
 *
 *     void onOnceBeforeDraw() override {
 *         fEmojiFont = ToolUtils::EmojiSample(fFormat);
 *
 *         int count = 0;
 *         const char* ch_ptr = fEmojiFont.sampleText;
 *         const char* ch_end = ch_ptr + strlen(ch_ptr);
 *         while (ch_ptr < ch_end && count < 2) {
 *             SkUnichar ch = SkUTF::NextUTF8(&ch_ptr, ch_end);
 *             if (ch != ' ') {
 *                 fStripSpacesSampleText.appendUnichar(ch);
 *                 ++count;
 *             }
 *         }
 *     }
 *
 *     SkString getName() const override {
 *         return SkString("scaledemojiperspective_") += ToolUtils::NameForFontFormat(fFormat);
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
 *         SkMatrix taper;
 *         taper.setPerspY(-0.0025f);
 *
 *         SkPaint paint;
 *         SkFont font(fEmojiFont.typeface, 40);
 *         sk_sp<SkTextBlob> blob = make_hpos_test_blob_utf8(fStripSpacesSampleText.c_str(), font);
 *
 *         // draw text at different point sizes
 *         // Testing GPU bitmap path, SDF path with no scaling,
 *         // SDF path with scaling, path rendering with scaling
 *         SkFontMetrics metrics;
 *         font.getMetrics(&metrics);
 *         for (auto rotate : {0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0}) {
 *             canvas->save();
 *             SkMatrix perspective;
 *             perspective.postTranslate(-600, -600);
 *             perspective.postConcat(taper);
 *             perspective.postRotate(rotate);
 *             perspective.postTranslate(600, 600);
 *             canvas->concat(perspective);
 *             SkScalar y = 670;
 *             for (int i = 0; i < 5; i++) {
 *
 *                 y += -metrics.fAscent;
 *
 *                 // Draw with an origin.
 *                 canvas->drawTextBlob(blob, 565, y, paint);
 *
 *                 y += metrics.fDescent + metrics.fLeading;
 *             }
 *             canvas->restore();
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
public open class ScaledEmojiPerspectiveGM public constructor(
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
   * SkString fStripSpacesSampleText
   * ```
   */
  protected var fStripSpacesSampleText: String = TODO("Initialize fStripSpacesSampleText")

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
   * void onOnceBeforeDraw() override {
   *         fEmojiFont = ToolUtils::EmojiSample(fFormat);
   *
   *         int count = 0;
   *         const char* ch_ptr = fEmojiFont.sampleText;
   *         const char* ch_end = ch_ptr + strlen(ch_ptr);
   *         while (ch_ptr < ch_end && count < 2) {
   *             SkUnichar ch = SkUTF::NextUTF8(&ch_ptr, ch_end);
   *             if (ch != ' ') {
   *                 fStripSpacesSampleText.appendUnichar(ch);
   *                 ++count;
   *             }
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
   * SkString getName() const override {
   *         return SkString("scaledemojiperspective_") += ToolUtils::NameForFontFormat(fFormat);
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
   *         SkMatrix taper;
   *         taper.setPerspY(-0.0025f);
   *
   *         SkPaint paint;
   *         SkFont font(fEmojiFont.typeface, 40);
   *         sk_sp<SkTextBlob> blob = make_hpos_test_blob_utf8(fStripSpacesSampleText.c_str(), font);
   *
   *         // draw text at different point sizes
   *         // Testing GPU bitmap path, SDF path with no scaling,
   *         // SDF path with scaling, path rendering with scaling
   *         SkFontMetrics metrics;
   *         font.getMetrics(&metrics);
   *         for (auto rotate : {0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0}) {
   *             canvas->save();
   *             SkMatrix perspective;
   *             perspective.postTranslate(-600, -600);
   *             perspective.postConcat(taper);
   *             perspective.postRotate(rotate);
   *             perspective.postTranslate(600, 600);
   *             canvas->concat(perspective);
   *             SkScalar y = 670;
   *             for (int i = 0; i < 5; i++) {
   *
   *                 y += -metrics.fAscent;
   *
   *                 // Draw with an origin.
   *                 canvas->drawTextBlob(blob, 565, y, paint);
   *
   *                 y += metrics.fDescent + metrics.fLeading;
   *             }
   *             canvas->restore();
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
