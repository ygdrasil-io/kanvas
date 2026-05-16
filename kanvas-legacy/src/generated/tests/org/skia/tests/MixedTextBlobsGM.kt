package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class MixedTextBlobsGM : public GM {
 * public:
 *     MixedTextBlobsGM() { }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         fEmojiTypeface      = ToolUtils::PlanetTypeface();
 *         fEmojiText = "♁♃";
 *         fReallyBigATypeface = ToolUtils::CreateTypefaceFromResource("fonts/ReallyBigA.ttf");
 *         if (!fReallyBigATypeface) {
 *             fReallyBigATypeface = ToolUtils::DefaultPortableTypeface();
 *         }
 *
 *         SkTextBlobBuilder builder;
 *
 *         // make textblob
 *         // Text so large we draw as paths
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 385);
 *         font.setEdging(SkFont::Edging::kAlias);
 *         const char* text = "O";
 *
 *         SkRect bounds;
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *
 *         SkScalar yOffset = bounds.height();
 *         ToolUtils::add_to_text_blob(&builder, text, font, 10, yOffset);
 *         SkScalar corruptedAx = bounds.width();
 *         SkScalar corruptedAy = yOffset;
 *
 *         const SkScalar boundsHalfWidth = bounds.width() * SK_ScalarHalf;
 *         const SkScalar boundsHalfHeight = bounds.height() * SK_ScalarHalf;
 *
 *         SkScalar xOffset = boundsHalfWidth;
 *         yOffset = boundsHalfHeight;
 *
 *         // LCD
 *         font.setSize(32);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         font.setSubpixel(true);
 *         text = "LCD!!!!!";
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         ToolUtils::add_to_text_blob(&builder,
 *                                     text,
 *                                     font,
 *                                     xOffset - bounds.width() * 0.25f,
 *                                     yOffset - bounds.height() * 0.5f);
 *
 *         // color emoji font with large glyph
 *         if (fEmojiTypeface) {
 *             font.setEdging(SkFont::Edging::kAlias);
 *             font.setSubpixel(false);
 *             font.setTypeface(fEmojiTypeface);
 *             font.measureText(fEmojiText, strlen(fEmojiText), SkTextEncoding::kUTF8, &bounds);
 *             ToolUtils::add_to_text_blob(&builder, fEmojiText, font, xOffset, yOffset);
 *         }
 *
 *         // outline font with large glyph
 *         font.setSize(12);
 *         text = "aA";
 *         font.setTypeface(fReallyBigATypeface);
 *         ToolUtils::add_to_text_blob(&builder, text, font, corruptedAx, corruptedAy);
 *         fBlob = builder.make();
 *     }
 *
 *     SkString getName() const override { return SkString("mixedtextblobs"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         canvas->drawColor(SK_ColorGRAY);
 *
 *         SkPaint paint;
 *
 *         // setup work needed to draw text with different clips
 *         paint.setColor(SK_ColorBLACK);
 *         canvas->translate(10, 40);
 *
 *         // compute the bounds of the text and setup some clips
 *         SkRect bounds = fBlob->bounds();
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
 *         const SkRect clipRects[] = { bounds, upperLeftClip, lowerRightClip, interiorClip};
 *
 *         size_t count = sizeof(clipRects) / sizeof(SkRect);
 *         for (size_t x = 0; x < count; ++x) {
 *             draw_blob(canvas, fBlob.get(), paint, clipRects[x]);
 *             if (x == (count >> 1) - 1) {
 *                 canvas->translate(SkScalarFloorToScalar(bounds.width() + SkIntToScalar(25)),
 *                                   -(x * SkScalarFloorToScalar(bounds.height() +
 *                                     SkIntToScalar(25))));
 *             } else {
 *                 canvas->translate(0, SkScalarFloorToScalar(bounds.height() + SkIntToScalar(25)));
 *             }
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkTypeface> fEmojiTypeface;
 *     sk_sp<SkTypeface> fReallyBigATypeface;
 *     const char* fEmojiText;
 *     sk_sp<SkTextBlob> fBlob;
 *
 *     inline static constexpr int kWidth = 1250;
 *     inline static constexpr int kHeight = 700;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class MixedTextBlobsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fEmojiTypeface
   * ```
   */
  private var fEmojiTypeface: SkSp<SkTypeface> = TODO("Initialize fEmojiTypeface")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fReallyBigATypeface
   * ```
   */
  private var fReallyBigATypeface: SkSp<SkTypeface> = TODO("Initialize fReallyBigATypeface")

  /**
   * C++ original:
   * ```cpp
   * const char* fEmojiText
   * ```
   */
  private val fEmojiText: String? = TODO("Initialize fEmojiText")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fBlob
   * ```
   */
  private var fBlob: SkSp<SkTextBlob> = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fEmojiTypeface      = ToolUtils::PlanetTypeface();
   *         fEmojiText = "♁♃";
   *         fReallyBigATypeface = ToolUtils::CreateTypefaceFromResource("fonts/ReallyBigA.ttf");
   *         if (!fReallyBigATypeface) {
   *             fReallyBigATypeface = ToolUtils::DefaultPortableTypeface();
   *         }
   *
   *         SkTextBlobBuilder builder;
   *
   *         // make textblob
   *         // Text so large we draw as paths
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 385);
   *         font.setEdging(SkFont::Edging::kAlias);
   *         const char* text = "O";
   *
   *         SkRect bounds;
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *
   *         SkScalar yOffset = bounds.height();
   *         ToolUtils::add_to_text_blob(&builder, text, font, 10, yOffset);
   *         SkScalar corruptedAx = bounds.width();
   *         SkScalar corruptedAy = yOffset;
   *
   *         const SkScalar boundsHalfWidth = bounds.width() * SK_ScalarHalf;
   *         const SkScalar boundsHalfHeight = bounds.height() * SK_ScalarHalf;
   *
   *         SkScalar xOffset = boundsHalfWidth;
   *         yOffset = boundsHalfHeight;
   *
   *         // LCD
   *         font.setSize(32);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         font.setSubpixel(true);
   *         text = "LCD!!!!!";
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         ToolUtils::add_to_text_blob(&builder,
   *                                     text,
   *                                     font,
   *                                     xOffset - bounds.width() * 0.25f,
   *                                     yOffset - bounds.height() * 0.5f);
   *
   *         // color emoji font with large glyph
   *         if (fEmojiTypeface) {
   *             font.setEdging(SkFont::Edging::kAlias);
   *             font.setSubpixel(false);
   *             font.setTypeface(fEmojiTypeface);
   *             font.measureText(fEmojiText, strlen(fEmojiText), SkTextEncoding::kUTF8, &bounds);
   *             ToolUtils::add_to_text_blob(&builder, fEmojiText, font, xOffset, yOffset);
   *         }
   *
   *         // outline font with large glyph
   *         font.setSize(12);
   *         text = "aA";
   *         font.setTypeface(fReallyBigATypeface);
   *         ToolUtils::add_to_text_blob(&builder, text, font, corruptedAx, corruptedAy);
   *         fBlob = builder.make();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("mixedtextblobs"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         canvas->drawColor(SK_ColorGRAY);
   *
   *         SkPaint paint;
   *
   *         // setup work needed to draw text with different clips
   *         paint.setColor(SK_ColorBLACK);
   *         canvas->translate(10, 40);
   *
   *         // compute the bounds of the text and setup some clips
   *         SkRect bounds = fBlob->bounds();
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
   *         const SkRect clipRects[] = { bounds, upperLeftClip, lowerRightClip, interiorClip};
   *
   *         size_t count = sizeof(clipRects) / sizeof(SkRect);
   *         for (size_t x = 0; x < count; ++x) {
   *             draw_blob(canvas, fBlob.get(), paint, clipRects[x]);
   *             if (x == (count >> 1) - 1) {
   *                 canvas->translate(SkScalarFloorToScalar(bounds.width() + SkIntToScalar(25)),
   *                                   -(x * SkScalarFloorToScalar(bounds.height() +
   *                                     SkIntToScalar(25))));
   *             } else {
   *                 canvas->translate(0, SkScalarFloorToScalar(bounds.height() + SkIntToScalar(25)));
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")
  }
}
