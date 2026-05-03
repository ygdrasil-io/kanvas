package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LcdOverlapGM : public skiagm::GM {
 * public:
 *     LcdOverlapGM() {
 *         const int kPointSize = 25;
 *         fTextHeight = SkIntToScalar(kPointSize);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("lcdoverlap"); }
 *
 *     void onOnceBeforeDraw() override {
 *         // build text blob
 *         SkTextBlobBuilder builder;
 *
 *         SkFont      font(ToolUtils::DefaultPortableTypeface(), 32);
 *         const char* text = "able was I ere I saw elba";
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, 0);
 *         fBlob = builder.make();
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void drawTestCase(SkCanvas* canvas, SkScalar x, SkScalar y, SkBlendMode mode,
 *                       SkBlendMode mode2) {
 *         const SkColor colors[] {
 *                 SK_ColorRED,
 *                 SK_ColorGREEN,
 *                 SK_ColorBLUE,
 *                 SK_ColorYELLOW,
 *                 SK_ColorCYAN,
 *                 SK_ColorMAGENTA,
 *         };
 *
 *         for (size_t i = 0; i < std::size(colors); i++) {
 *             canvas->save();
 *             canvas->translate(x, y);
 *             canvas->rotate(360.0f / std::size(colors) * i);
 *             canvas->translate(-fBlob->bounds().width() / 2.0f - fBlob->bounds().left() + 0.5f, 0);
 *
 *             SkPaint textPaint;
 *             textPaint.setColor(colors[i]);
 *             textPaint.setBlendMode(i % 2 == 0 ? mode : mode2);
 *             canvas->drawTextBlob(fBlob, 0, 0, textPaint);
 *             canvas->restore();
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar offsetX = kWidth / 4.0f;
 *         SkScalar offsetY = kHeight / 4.0f;
 *         drawTestCase(canvas, offsetX, offsetY,  SkBlendMode::kSrc, SkBlendMode::kSrc);
 *         drawTestCase(canvas, 3 * offsetX, offsetY,  SkBlendMode::kSrcOver, SkBlendMode::kSrcOver);
 *         drawTestCase(canvas, offsetX, 3 * offsetY,  SkBlendMode::kHardLight,
 *                      SkBlendMode::kLuminosity);
 *         drawTestCase(canvas, 3 * offsetX, 3 * offsetY,  SkBlendMode::kSrcOver, SkBlendMode::kSrc);
 *     }
 *
 * private:
 *     SkScalar fTextHeight;
 *     sk_sp<SkTextBlob> fBlob;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class LcdOverlapGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fTextHeight
   * ```
   */
  private var fTextHeight: SkScalar = TODO("Initialize fTextHeight")

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
   * SkString getName() const override { return SkString("lcdoverlap"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // build text blob
   *         SkTextBlobBuilder builder;
   *
   *         SkFont      font(ToolUtils::DefaultPortableTypeface(), 32);
   *         const char* text = "able was I ere I saw elba";
   *         font.setSubpixel(true);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, 0);
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
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTestCase(SkCanvas* canvas, SkScalar x, SkScalar y, SkBlendMode mode,
   *                       SkBlendMode mode2) {
   *         const SkColor colors[] {
   *                 SK_ColorRED,
   *                 SK_ColorGREEN,
   *                 SK_ColorBLUE,
   *                 SK_ColorYELLOW,
   *                 SK_ColorCYAN,
   *                 SK_ColorMAGENTA,
   *         };
   *
   *         for (size_t i = 0; i < std::size(colors); i++) {
   *             canvas->save();
   *             canvas->translate(x, y);
   *             canvas->rotate(360.0f / std::size(colors) * i);
   *             canvas->translate(-fBlob->bounds().width() / 2.0f - fBlob->bounds().left() + 0.5f, 0);
   *
   *             SkPaint textPaint;
   *             textPaint.setColor(colors[i]);
   *             textPaint.setBlendMode(i % 2 == 0 ? mode : mode2);
   *             canvas->drawTextBlob(fBlob, 0, 0, textPaint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected fun drawTestCase(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
    mode: SkBlendMode,
    mode2: SkBlendMode,
  ) {
    TODO("Implement drawTestCase")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar offsetX = kWidth / 4.0f;
   *         SkScalar offsetY = kHeight / 4.0f;
   *         drawTestCase(canvas, offsetX, offsetY,  SkBlendMode::kSrc, SkBlendMode::kSrc);
   *         drawTestCase(canvas, 3 * offsetX, offsetY,  SkBlendMode::kSrcOver, SkBlendMode::kSrcOver);
   *         drawTestCase(canvas, offsetX, 3 * offsetY,  SkBlendMode::kHardLight,
   *                      SkBlendMode::kLuminosity);
   *         drawTestCase(canvas, 3 * offsetX, 3 * offsetY,  SkBlendMode::kSrcOver, SkBlendMode::kSrc);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
