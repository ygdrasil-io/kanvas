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
 * class TextBlobColorTrans : public GM {
 * public:
 *     // This gm tests that textblobs can be translated and have their colors regenerated
 *     // correctly.  With smaller atlas sizes, it can also trigger regeneration of texture coords on
 *     // the GPU backend
 *     TextBlobColorTrans() { }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkTextBlobBuilder builder;
 *
 *         // make textblob
 *         // Large text is used to trigger atlas eviction
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
 *         font.setEdging(SkFont::Edging::kAlias);
 *         const char* text = "AB";
 *
 *         SkRect bounds;
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *
 *         SkScalar yOffset = bounds.height();
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 30);
 *
 *         // A8
 *         font.setSize(28);
 *         text = "The quick brown fox jumps over the lazy dog.";
 *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 8);
 *
 *         // build
 *         fBlob = builder.make();
 *     }
 *
 *     SkString getName() const override { return SkString("textblobcolortrans"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         canvas->drawColor(SK_ColorGRAY);
 *
 *         SkPaint paint;
 *         canvas->translate(10, 40);
 *
 *         SkRect bounds = fBlob->bounds();
 *
 *         // Colors were chosen to map to pairs of canonical colors.  The GPU Backend will cache A8
 *         // Texture Blobs based on the canonical color they map to.  Canonical colors are used to
 *         // create masks.  For A8 there are 8 of them.
 *         SkColor colors[] = {SK_ColorCYAN, SK_ColorLTGRAY, SK_ColorYELLOW, SK_ColorWHITE};
 *
 *         size_t count = std::size(colors);
 *         size_t colorIndex = 0;
 *         for (int y = 0; y + SkScalarFloorToInt(bounds.height()) < kHeight;
 *              y += SkScalarFloorToInt(bounds.height())) {
 *             paint.setColor(colors[colorIndex++ % count]);
 *             canvas->save();
 *             canvas->translate(0, SkIntToScalar(y));
 *             canvas->drawTextBlob(fBlob, 0, 0, paint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkTextBlob> fBlob;
 *
 *     inline static constexpr int kWidth = 675;
 *     inline static constexpr int kHeight = 1600;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class TextBlobColorTrans public constructor() : GM() {
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
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
   *         font.setEdging(SkFont::Edging::kAlias);
   *         const char* text = "AB";
   *
   *         SkRect bounds;
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *
   *         SkScalar yOffset = bounds.height();
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 30);
   *
   *         // A8
   *         font.setSize(28);
   *         text = "The quick brown fox jumps over the lazy dog.";
   *         font.measureText(text, strlen(text), SkTextEncoding::kUTF8, &bounds);
   *         ToolUtils::add_to_text_blob(&builder, text, font, 0, yOffset - 8);
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
   * SkString getName() const override { return SkString("textblobcolortrans"); }
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
   *         canvas->translate(10, 40);
   *
   *         SkRect bounds = fBlob->bounds();
   *
   *         // Colors were chosen to map to pairs of canonical colors.  The GPU Backend will cache A8
   *         // Texture Blobs based on the canonical color they map to.  Canonical colors are used to
   *         // create masks.  For A8 there are 8 of them.
   *         SkColor colors[] = {SK_ColorCYAN, SK_ColorLTGRAY, SK_ColorYELLOW, SK_ColorWHITE};
   *
   *         size_t count = std::size(colors);
   *         size_t colorIndex = 0;
   *         for (int y = 0; y + SkScalarFloorToInt(bounds.height()) < kHeight;
   *              y += SkScalarFloorToInt(bounds.height())) {
   *             paint.setColor(colors[colorIndex++ % count]);
   *             canvas->save();
   *             canvas->translate(0, SkIntToScalar(y));
   *             canvas->drawTextBlob(fBlob, 0, 0, paint);
   *             canvas->restore();
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
