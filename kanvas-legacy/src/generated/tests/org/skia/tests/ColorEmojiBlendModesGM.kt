package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize
import org.skia.tools.EmojiFontFormat
import org.skia.tools.EmojiTestSample

/**
 * C++ original:
 * ```cpp
 * class ColorEmojiBlendModesGM : public skiagm::GM {
 * public:
 *     const static int W = 64;
 *     const static int H = 64;
 *     ColorEmojiBlendModesGM(ToolUtils::EmojiFontFormat format) : fFormat(format) {}
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         const SkColor4f colors[] = {
 *             SkColors::kRed, SkColors::kGreen, SkColors::kBlue,
 *             SkColors::kMagenta, SkColors::kCyan, SkColors::kYellow
 *         };
 *         SkMatrix local;
 *         local.setRotate(180);
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setShader(SkShaders::SweepGradient({0, 0},
 *                                                  {{colors, {}, SkTileMode::kClamp}, {}}, &local));
 *
 *         sk_sp<SkTypeface> orig(ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold()));
 *         SkASSERT(orig);
 *         fColorSample = ToolUtils::EmojiSample(fFormat);
 *
 *         fBG.installPixels(SkImageInfo::Make(2, 2, kARGB_4444_SkColorType,
 *                                             kOpaque_SkAlphaType), gData, 4);
 *     }
 *
 *     SkString getName() const override {
 *         return SkString("coloremoji_blendmodes_") += ToolUtils::NameForFontFormat(fFormat);
 *     }
 *
 *     SkISize getISize() override { return {400, 640}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *
 *         if (!fColorSample.typeface) {
 *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
 *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
 *             return DrawResult::kSkip;
 *         }
 *
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
 *
 *         const SkBlendMode gModes[] = {
 *             SkBlendMode::kClear,
 *             SkBlendMode::kSrc,
 *             SkBlendMode::kDst,
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *             SkBlendMode::kSrcOut,
 *             SkBlendMode::kDstOut,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *
 *             SkBlendMode::kXor,
 *             SkBlendMode::kPlus,
 *             SkBlendMode::kModulate,
 *             SkBlendMode::kScreen,
 *             SkBlendMode::kOverlay,
 *             SkBlendMode::kDarken,
 *             SkBlendMode::kLighten,
 *             SkBlendMode::kColorDodge,
 *             SkBlendMode::kColorBurn,
 *             SkBlendMode::kHardLight,
 *             SkBlendMode::kSoftLight,
 *             SkBlendMode::kDifference,
 *             SkBlendMode::kExclusion,
 *             SkBlendMode::kMultiply,
 *             SkBlendMode::kHue,
 *             SkBlendMode::kSaturation,
 *             SkBlendMode::kColor,
 *             SkBlendMode::kLuminosity,
 *         };
 *
 *         const SkScalar w = SkIntToScalar(W);
 *         const SkScalar h = SkIntToScalar(H);
 *         SkMatrix m;
 *         m.setScale(SkIntToScalar(6), SkIntToScalar(6));
 *         auto s = fBG.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkSamplingOptions(), m);
 *
 *         SkFont labelFont(ToolUtils::DefaultPortableTypeface());
 *
 *         SkPaint textP;
 *         textP.setAntiAlias(true);
 *         SkFont textFont(fColorSample.typeface, 70);
 *
 *         const int kWrap = 5;
 *
 *         SkScalar x0 = 0;
 *         SkScalar y0 = 0;
 *         SkScalar x = x0, y = y0;
 *         for (size_t i = 0; i < std::size(gModes); i++) {
 *             SkRect r;
 *             r.setLTRB(x, y, x+w, y+h);
 *
 *             SkPaint p;
 *             p.setStyle(SkPaint::kFill_Style);
 *             p.setShader(s);
 *             canvas->drawRect(r, p);
 *
 *             r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
 *             p.setStyle(SkPaint::kStroke_Style);
 *             p.setShader(nullptr);
 *             canvas->drawRect(r, p);
 *
 *             {
 *                 SkAutoCanvasRestore arc(canvas, true);
 *                 canvas->clipRect(r);
 *                 textP.setBlendMode(gModes[i]);
 *                 const char* text    = fColorSample.sampleText;
 *                 SkUnichar unichar = SkUTF::NextUTF8(&text, text + strlen(text));
 *                 SkASSERT(unichar >= 0);
 *                 canvas->drawSimpleText(&unichar, 4, SkTextEncoding::kUTF32,
 *                                        x+ w/10.f, y + 7.f*h/8.f, textFont, textP);
 *             }
 * #if 1
 *             const char* label = SkBlendMode_Name(gModes[i]);
 *             SkTextUtils::DrawString(canvas, label, x + w/2, y - labelFont.getSize()/2,
 *                                     labelFont, SkPaint(), SkTextUtils::kCenter_Align);
 * #endif
 *             x += w + SkIntToScalar(10);
 *             if ((i % kWrap) == kWrap - 1) {
 *                 x = x0;
 *                 y += h + SkIntToScalar(30);
 *             }
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     SkBitmap fBG;
 *     ToolUtils::EmojiFontFormat fFormat;
 *     ToolUtils::EmojiTestSample fColorSample;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ColorEmojiBlendModesGM public constructor(
  format: EmojiFontFormat,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const static int W = 64
   * ```
   */
  private var fBG: SkBitmap = TODO("Initialize fBG")

  /**
   * C++ original:
   * ```cpp
   * const static int H = 64
   * ```
   */
  private var fFormat: EmojiFontFormat = TODO("Initialize fFormat")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBG
   * ```
   */
  private var fColorSample: EmojiTestSample = TODO("Initialize fColorSample")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkColor4f colors[] = {
   *             SkColors::kRed, SkColors::kGreen, SkColors::kBlue,
   *             SkColors::kMagenta, SkColors::kCyan, SkColors::kYellow
   *         };
   *         SkMatrix local;
   *         local.setRotate(180);
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setShader(SkShaders::SweepGradient({0, 0},
   *                                                  {{colors, {}, SkTileMode::kClamp}, {}}, &local));
   *
   *         sk_sp<SkTypeface> orig(ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold()));
   *         SkASSERT(orig);
   *         fColorSample = ToolUtils::EmojiSample(fFormat);
   *
   *         fBG.installPixels(SkImageInfo::Make(2, 2, kARGB_4444_SkColorType,
   *                                             kOpaque_SkAlphaType), gData, 4);
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
   *         return SkString("coloremoji_blendmodes_") += ToolUtils::NameForFontFormat(fFormat);
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {400, 640}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *
   *         if (!fColorSample.typeface) {
   *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
   *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
   *             return DrawResult::kSkip;
   *         }
   *
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(20));
   *
   *         const SkBlendMode gModes[] = {
   *             SkBlendMode::kClear,
   *             SkBlendMode::kSrc,
   *             SkBlendMode::kDst,
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *             SkBlendMode::kSrcOut,
   *             SkBlendMode::kDstOut,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *
   *             SkBlendMode::kXor,
   *             SkBlendMode::kPlus,
   *             SkBlendMode::kModulate,
   *             SkBlendMode::kScreen,
   *             SkBlendMode::kOverlay,
   *             SkBlendMode::kDarken,
   *             SkBlendMode::kLighten,
   *             SkBlendMode::kColorDodge,
   *             SkBlendMode::kColorBurn,
   *             SkBlendMode::kHardLight,
   *             SkBlendMode::kSoftLight,
   *             SkBlendMode::kDifference,
   *             SkBlendMode::kExclusion,
   *             SkBlendMode::kMultiply,
   *             SkBlendMode::kHue,
   *             SkBlendMode::kSaturation,
   *             SkBlendMode::kColor,
   *             SkBlendMode::kLuminosity,
   *         };
   *
   *         const SkScalar w = SkIntToScalar(W);
   *         const SkScalar h = SkIntToScalar(H);
   *         SkMatrix m;
   *         m.setScale(SkIntToScalar(6), SkIntToScalar(6));
   *         auto s = fBG.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkSamplingOptions(), m);
   *
   *         SkFont labelFont(ToolUtils::DefaultPortableTypeface());
   *
   *         SkPaint textP;
   *         textP.setAntiAlias(true);
   *         SkFont textFont(fColorSample.typeface, 70);
   *
   *         const int kWrap = 5;
   *
   *         SkScalar x0 = 0;
   *         SkScalar y0 = 0;
   *         SkScalar x = x0, y = y0;
   *         for (size_t i = 0; i < std::size(gModes); i++) {
   *             SkRect r;
   *             r.setLTRB(x, y, x+w, y+h);
   *
   *             SkPaint p;
   *             p.setStyle(SkPaint::kFill_Style);
   *             p.setShader(s);
   *             canvas->drawRect(r, p);
   *
   *             r.inset(-SK_ScalarHalf, -SK_ScalarHalf);
   *             p.setStyle(SkPaint::kStroke_Style);
   *             p.setShader(nullptr);
   *             canvas->drawRect(r, p);
   *
   *             {
   *                 SkAutoCanvasRestore arc(canvas, true);
   *                 canvas->clipRect(r);
   *                 textP.setBlendMode(gModes[i]);
   *                 const char* text    = fColorSample.sampleText;
   *                 SkUnichar unichar = SkUTF::NextUTF8(&text, text + strlen(text));
   *                 SkASSERT(unichar >= 0);
   *                 canvas->drawSimpleText(&unichar, 4, SkTextEncoding::kUTF32,
   *                                        x+ w/10.f, y + 7.f*h/8.f, textFont, textP);
   *             }
   * #if 1
   *             const char* label = SkBlendMode_Name(gModes[i]);
   *             SkTextUtils::DrawString(canvas, label, x + w/2, y - labelFont.getSize()/2,
   *                                     labelFont, SkPaint(), SkTextUtils::kCenter_Align);
   * #endif
   *             x += w + SkIntToScalar(10);
   *             if ((i % kWrap) == kWrap - 1) {
   *                 x = x0;
   *                 y += h + SkIntToScalar(30);
   *             }
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    public val w: Int = TODO("Initialize w")

    public val h: Int = TODO("Initialize h")
  }
}
