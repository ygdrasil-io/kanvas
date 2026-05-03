package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TextBlobBlockReordering : public GM {
 * public:
 *     // This gm tests that textblobs translate properly when their draw order is different from their
 *     // flush order
 *     TextBlobBlockReordering() { }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkTextBlobBuilder builder;
 *
 *         // make textblob
 *         // Large text is used to trigger atlas eviction
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 56);
 *         font.setEdging(SkFont::Edging::kAlias);
 *         const char* text = "AB";
 *
 *         SkRect bounds;
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *
 *         SkScalar yOffset = bounds.height();
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 30);
 *
 *         // build
 *         fBlob = builder.make();
 *     }
 *
 *     SkString getName() const override { return SkString("textblobblockreordering"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     // This draws the same text blob 3 times.  The second draw used a different xfer mode so its
 *     // GrDrawOp doesn't get combined with the first and third. Ultimately, they will be flushed in
 *     // the order first, third, and then second.
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(SK_ColorGRAY);
 *
 *         SkPaint paint;
 *         canvas->translate(10, 40);
 *
 *         SkRect bounds = fBlob->bounds();
 *         const int yDelta = SkScalarFloorToInt(bounds.height()) + 20;
 *         const int xDelta = SkScalarFloorToInt(bounds.width());
 *
 *         canvas->drawTextBlob(fBlob, 0, 0, paint);
 *
 *         canvas->translate(SkIntToScalar(xDelta), SkIntToScalar(yDelta));
 *
 *         // Draw a rect where the text should be, and then twiddle the xfermode so we don't combine.
 *         SkPaint redPaint;
 *         redPaint.setColor(SK_ColorRED);
 *         canvas->drawRect(bounds, redPaint);
 *         SkPaint srcInPaint(paint);
 *         srcInPaint.setBlendMode(SkBlendMode::kSrcIn);
 *         canvas->drawTextBlob(fBlob, 0, 0, srcInPaint);
 *
 *         canvas->translate(SkIntToScalar(xDelta), SkIntToScalar(yDelta));
 *         canvas->drawTextBlob(fBlob, 0, 0, paint);
 *     }
 *
 * private:
 *     sk_sp<SkTextBlob> fBlob;
 *
 *     inline static constexpr int kWidth = 275;
 *     inline static constexpr int kHeight = 200;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TextBlobBlockReordering public constructor() : GM() {
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
   *         SkTextBlobBuilder builder;
   *
   *         // make textblob
   *         // Large text is used to trigger atlas eviction
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 56);
   *         font.setEdging(SkFont::Edging::kAlias);
   *         const char* text = "AB";
   *
   *         SkRect bounds;
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *
   *         SkScalar yOffset = bounds.height();
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 30);
   *
   *         // build
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
   * SkString getName() const override { return SkString("textblobblockreordering"); }
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
   *         canvas->drawColor(SK_ColorGRAY);
   *
   *         SkPaint paint;
   *         canvas->translate(10, 40);
   *
   *         SkRect bounds = fBlob->bounds();
   *         const int yDelta = SkScalarFloorToInt(bounds.height()) + 20;
   *         const int xDelta = SkScalarFloorToInt(bounds.width());
   *
   *         canvas->drawTextBlob(fBlob, 0, 0, paint);
   *
   *         canvas->translate(SkIntToScalar(xDelta), SkIntToScalar(yDelta));
   *
   *         // Draw a rect where the text should be, and then twiddle the xfermode so we don't combine.
   *         SkPaint redPaint;
   *         redPaint.setColor(SK_ColorRED);
   *         canvas->drawRect(bounds, redPaint);
   *         SkPaint srcInPaint(paint);
   *         srcInPaint.setBlendMode(SkBlendMode::kSrcIn);
   *         canvas->drawTextBlob(fBlob, 0, 0, srcInPaint);
   *
   *         canvas->translate(SkIntToScalar(xDelta), SkIntToScalar(yDelta));
   *         canvas->drawTextBlob(fBlob, 0, 0, paint);
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
