package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize
import org.skia.tools.EmojiFontFormat
import org.skia.tools.EmojiTestSample

/**
 * C++ original:
 * ```cpp
 * class ColorEmojiGM : public GM {
 * public:
 *     ColorEmojiGM(ToolUtils::EmojiFontFormat format) : fFormat(format) {}
 *
 * protected:
 *     ToolUtils::EmojiTestSample emojiFont;
 *     void onOnceBeforeDraw() override {
 *         emojiFont = ToolUtils::EmojiSample(fFormat);
 *     }
 *
 *     SkString getName() const override {
 *         return SkString("coloremoji_") += ToolUtils::NameForFontFormat(fFormat);
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(650, 1200); }
 *
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* ctxOptions) override {
 *         // This will force multitexturing to verify that color text works with this,
 *         // as well as with any additional color transformations.
 *         ctxOptions->fGlyphCacheTextureMaximumBytes = 256 * 256 * 4;
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* ctxOptions) const override {
 *         // This will force multitexturing to verify that color text works with this,
 *         // as well as with any additional color transformations.
 *         ctxOptions->fGlyphCacheTextureMaximumBytes = 256 * 256 * 4;
 *     }
 * #endif
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         canvas->drawColor(SK_ColorGRAY);
 *
 *         if (!emojiFont.typeface) {
 *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
 *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
 *             return DrawResult::kSkip;
 *         }
 *
 *         SkFont font(emojiFont.typeface);
 *         char const * const text = emojiFont.sampleText;
 *         size_t textLen = strlen(text);
 *
 *         // draw text at different point sizes
 *         constexpr SkScalar textSizes[] = { 10, 30, 50 };
 *         SkFontMetrics metrics;
 *         SkScalar y = 0;
 *         for (const bool& fakeBold : { false, true }) {
 *             font.setEmbolden(fakeBold);
 *             for (const SkScalar& textSize : textSizes) {
 *                 font.setSize(textSize);
 *                 font.getMetrics(&metrics);
 *                 y += -metrics.fAscent;
 *                 canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8,
 *                                        10, y, font, SkPaint());
 *                 y += metrics.fDescent + metrics.fLeading;
 *             }
 *         }
 *
 *         // draw one more big one to max out one Plot
 *         font.setSize(256);
 *         font.getMetrics(&metrics);
 *         canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8,
 *                                190, -metrics.fAscent, font, SkPaint());
 *
 *         y += 20;
 *         SkScalar savedY = y;
 *         // draw with shaders and image filters
 *         for (int makeLinear = 0; makeLinear < 2; makeLinear++) {
 *             for (int makeBlur = 0; makeBlur < 2; makeBlur++) {
 *                 for (int makeGray = 0; makeGray < 2; makeGray++) {
 *                     for (int makeMode = 0; makeMode < 2; ++makeMode) {
 *                         for (int alpha = 0; alpha < 2; ++alpha) {
 *                             SkFont shaderFont(font.refTypeface());
 *                             SkPaint shaderPaint;
 *                             if (SkToBool(makeLinear)) {
 *                                 shaderPaint.setShader(MakeLinear());
 *                             }
 *
 *                             if (SkToBool(makeBlur) && SkToBool(makeGray)) {
 *                                 sk_sp<SkImageFilter> grayScale(make_grayscale(nullptr));
 *                                 sk_sp<SkImageFilter> blur(make_blur(3.0f, std::move(grayScale)));
 *                                 shaderPaint.setImageFilter(std::move(blur));
 *                             } else if (SkToBool(makeBlur)) {
 *                                 shaderPaint.setImageFilter(make_blur(3.0f, nullptr));
 *                             } else if (SkToBool(makeGray)) {
 *                                 shaderPaint.setImageFilter(make_grayscale(nullptr));
 *                             }
 *                             if (makeMode) {
 *                                 shaderPaint.setColorFilter(make_color_filter());
 *                             }
 *                             if (alpha) {
 *                                 shaderPaint.setAlphaf(0.5f);
 *                             }
 *                             shaderFont.setSize(30);
 *                             shaderFont.getMetrics(&metrics);
 *                             y += -metrics.fAscent;
 *                             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 380, y,
 *                                                    shaderFont, shaderPaint);
 *                             y += metrics.fDescent + metrics.fLeading;
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *         // setup work needed to draw text with different clips
 *         canvas->translate(10, savedY);
 *         font.setSize(40);
 *
 *         // compute the bounds of the text
 *         SkRect bounds;
 *         font.measureText(text, textLen, SkTextEncoding::kUTF8, &bounds);
 *
 *         const SkScalar boundsHalfWidth = bounds.width() * SK_ScalarHalf;
 *         const SkScalar boundsHalfHeight = bounds.height() * SK_ScalarHalf;
 *         const SkScalar boundsQuarterWidth = boundsHalfWidth * SK_ScalarHalf;
 *         const SkScalar boundsQuarterHeight = boundsHalfHeight * SK_ScalarHalf;
 *
 *         SkRect upperLeftClip = SkRect::MakeXYWH(bounds.left(), bounds.top(),
 *                                                 boundsHalfWidth, boundsHalfHeight);
 *         SkRect lowerRightClip = SkRect::MakeXYWH(bounds.centerX(), bounds.centerY(),
 *                                                  boundsHalfWidth, boundsHalfHeight);
 *         SkRect interiorClip = bounds;
 *         interiorClip.inset(boundsQuarterWidth, boundsQuarterHeight);
 *
 *         const SkRect clipRects[] = { bounds, upperLeftClip, lowerRightClip, interiorClip };
 *
 *         SkPaint clipHairline;
 *         clipHairline.setColor(SK_ColorWHITE);
 *         clipHairline.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPaint paint;
 *         for (const SkRect& clipRect : clipRects) {
 *             canvas->translate(0, bounds.height());
 *             canvas->save();
 *             canvas->drawRect(clipRect, clipHairline);
 *             paint.setAlpha(0x20);
 *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 0, 0, font, paint);
 *             canvas->clipRect(clipRect);
 *             paint.setAlphaf(1.0f);
 *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 0, 0, font, paint);
 *             canvas->restore();
 *             canvas->translate(0, SkIntToScalar(25));
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     ToolUtils::EmojiFontFormat fFormat;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ColorEmojiGM public constructor(
  format: EmojiFontFormat,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * ToolUtils::EmojiTestSample emojiFont
   * ```
   */
  protected var emojiFont: EmojiTestSample = TODO("Initialize emojiFont")

  /**
   * C++ original:
   * ```cpp
   * ToolUtils::EmojiFontFormat fFormat
   * ```
   */
  protected var fFormat: EmojiFontFormat = TODO("Initialize fFormat")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         emojiFont = ToolUtils::EmojiSample(fFormat);
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
   *         return SkString("coloremoji_") += ToolUtils::NameForFontFormat(fFormat);
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(650, 1200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* ctxOptions) const override {
   *         // This will force multitexturing to verify that color text works with this,
   *         // as well as with any additional color transformations.
   *         ctxOptions->fGlyphCacheTextureMaximumBytes = 256 * 256 * 4;
   *     }
   * ```
   */
  protected override fun modifyGraphiteContextOptions(ctxOptions: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         canvas->drawColor(SK_ColorGRAY);
   *
   *         if (!emojiFont.typeface) {
   *             *errorMsg = SkStringPrintf("Unable to instantiate emoji test font of format %s.",
   *                                        ToolUtils::NameForFontFormat(fFormat).c_str());
   *             return DrawResult::kSkip;
   *         }
   *
   *         SkFont font(emojiFont.typeface);
   *         char const * const text = emojiFont.sampleText;
   *         size_t textLen = strlen(text);
   *
   *         // draw text at different point sizes
   *         constexpr SkScalar textSizes[] = { 10, 30, 50 };
   *         SkFontMetrics metrics;
   *         SkScalar y = 0;
   *         for (const bool& fakeBold : { false, true }) {
   *             font.setEmbolden(fakeBold);
   *             for (const SkScalar& textSize : textSizes) {
   *                 font.setSize(textSize);
   *                 font.getMetrics(&metrics);
   *                 y += -metrics.fAscent;
   *                 canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8,
   *                                        10, y, font, SkPaint());
   *                 y += metrics.fDescent + metrics.fLeading;
   *             }
   *         }
   *
   *         // draw one more big one to max out one Plot
   *         font.setSize(256);
   *         font.getMetrics(&metrics);
   *         canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8,
   *                                190, -metrics.fAscent, font, SkPaint());
   *
   *         y += 20;
   *         SkScalar savedY = y;
   *         // draw with shaders and image filters
   *         for (int makeLinear = 0; makeLinear < 2; makeLinear++) {
   *             for (int makeBlur = 0; makeBlur < 2; makeBlur++) {
   *                 for (int makeGray = 0; makeGray < 2; makeGray++) {
   *                     for (int makeMode = 0; makeMode < 2; ++makeMode) {
   *                         for (int alpha = 0; alpha < 2; ++alpha) {
   *                             SkFont shaderFont(font.refTypeface());
   *                             SkPaint shaderPaint;
   *                             if (SkToBool(makeLinear)) {
   *                                 shaderPaint.setShader(MakeLinear());
   *                             }
   *
   *                             if (SkToBool(makeBlur) && SkToBool(makeGray)) {
   *                                 sk_sp<SkImageFilter> grayScale(make_grayscale(nullptr));
   *                                 sk_sp<SkImageFilter> blur(make_blur(3.0f, std::move(grayScale)));
   *                                 shaderPaint.setImageFilter(std::move(blur));
   *                             } else if (SkToBool(makeBlur)) {
   *                                 shaderPaint.setImageFilter(make_blur(3.0f, nullptr));
   *                             } else if (SkToBool(makeGray)) {
   *                                 shaderPaint.setImageFilter(make_grayscale(nullptr));
   *                             }
   *                             if (makeMode) {
   *                                 shaderPaint.setColorFilter(make_color_filter());
   *                             }
   *                             if (alpha) {
   *                                 shaderPaint.setAlphaf(0.5f);
   *                             }
   *                             shaderFont.setSize(30);
   *                             shaderFont.getMetrics(&metrics);
   *                             y += -metrics.fAscent;
   *                             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 380, y,
   *                                                    shaderFont, shaderPaint);
   *                             y += metrics.fDescent + metrics.fLeading;
   *                         }
   *                     }
   *                 }
   *             }
   *         }
   *         // setup work needed to draw text with different clips
   *         canvas->translate(10, savedY);
   *         font.setSize(40);
   *
   *         // compute the bounds of the text
   *         SkRect bounds;
   *         font.measureText(text, textLen, SkTextEncoding::kUTF8, &bounds);
   *
   *         const SkScalar boundsHalfWidth = bounds.width() * SK_ScalarHalf;
   *         const SkScalar boundsHalfHeight = bounds.height() * SK_ScalarHalf;
   *         const SkScalar boundsQuarterWidth = boundsHalfWidth * SK_ScalarHalf;
   *         const SkScalar boundsQuarterHeight = boundsHalfHeight * SK_ScalarHalf;
   *
   *         SkRect upperLeftClip = SkRect::MakeXYWH(bounds.left(), bounds.top(),
   *                                                 boundsHalfWidth, boundsHalfHeight);
   *         SkRect lowerRightClip = SkRect::MakeXYWH(bounds.centerX(), bounds.centerY(),
   *                                                  boundsHalfWidth, boundsHalfHeight);
   *         SkRect interiorClip = bounds;
   *         interiorClip.inset(boundsQuarterWidth, boundsQuarterHeight);
   *
   *         const SkRect clipRects[] = { bounds, upperLeftClip, lowerRightClip, interiorClip };
   *
   *         SkPaint clipHairline;
   *         clipHairline.setColor(SK_ColorWHITE);
   *         clipHairline.setStyle(SkPaint::kStroke_Style);
   *
   *         SkPaint paint;
   *         for (const SkRect& clipRect : clipRects) {
   *             canvas->translate(0, bounds.height());
   *             canvas->save();
   *             canvas->drawRect(clipRect, clipHairline);
   *             paint.setAlpha(0x20);
   *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 0, 0, font, paint);
   *             canvas->clipRect(clipRect);
   *             paint.setAlphaf(1.0f);
   *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, 0, 0, font, paint);
   *             canvas->restore();
   *             canvas->translate(0, SkIntToScalar(25));
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
