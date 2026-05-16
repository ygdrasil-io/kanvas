package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.gpu.ContextOptions
import org.skia.math.SkISize
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SlugGM : public skiagm::GM {
 * public:
 *     SlugGM(const char* txt) : fText(txt) {}
 *
 * protected:
 * #if defined(SK_GANESH)
 *     void modifyGrContextOptions(GrContextOptions* ctxOptions) override {
 *         ctxOptions->fSupportBilerpFromGlyphAtlas = true;
 *     }
 * #endif
 *
 * #if defined(SK_GRAPHITE)
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
 *         options->fSupportBilerpFromGlyphAtlas = true;
 *     }
 * #endif
 *
 *     void onOnceBeforeDraw() override {
 *         fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
 *         SkFont font(fTypeface);
 *         size_t txtLen = strlen(fText);
 *         int glyphCount = font.countText(fText, txtLen, SkTextEncoding::kUTF8);
 *
 *         fGlyphs.append(glyphCount);
 *         font.textToGlyphs(fText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
 *     }
 *
 *     SkString getName() const override { return SkString("slug"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1000, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkTextBlob> blob(this->makeBlob());
 *         SkPaint p;
 *         p.setAntiAlias(true);
 *         canvas->clipIRect(SkIRect::MakeSize(this->getISize()).makeInset(40, 50));
 *         canvas->scale(1.3f, 1.3f);
 *         sk_sp<sktext::gpu::Slug> slug = sktext::gpu::Slug::ConvertBlob(canvas, *blob, {10, 10}, p);
 *         if (slug == nullptr) {
 *             return;
 *         }
 *         canvas->translate(0.5, 0.5);
 *         canvas->translate(30, 30);
 *         canvas->drawTextBlob(blob, 10, 10, p);
 *         canvas->translate(370, 0);
 *         slug->draw(canvas, p);
 *         for (float scale = 1.5; scale < 4; scale += 0.5) {
 *             canvas->translate(-370, 20 * scale);
 *             canvas->save();
 *             canvas->scale(scale, scale);
 *             canvas->rotate(5);
 *             canvas->drawTextBlob(blob, 10, 10, p);
 *             canvas->restore();
 *             canvas->translate(370, 0);
 *             canvas->save();
 *             canvas->scale(scale, scale);
 *             canvas->rotate(5);
 *
 *             slug->draw(canvas, p);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkTextBlob> makeBlob() {
 *         SkTextBlobBuilder builder;
 *
 *         SkFont font;
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setTypeface(fTypeface);
 *         font.setSize(16);
 *
 *         const SkTextBlobBuilder::RunBuffer& buf = builder.allocRun(font, fGlyphs.size(), 0, 0);
 *         memcpy(buf.glyphs, fGlyphs.begin(), fGlyphs.size() * sizeof(uint16_t));
 *         return builder.make();
 *     }
 *
 *     SkTDArray<SkGlyphID> fGlyphs;
 *     sk_sp<SkTypeface>   fTypeface;
 *     const char*         fText;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class SlugGM public constructor(
  txt: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkGlyphID> fGlyphs
   * ```
   */
  private var fGlyphs: SkTDArray<SkGlyphID> = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>   fTypeface
   * ```
   */
  private var fTypeface: SkSp<SkTypeface> = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * const char*         fText
   * ```
   */
  private val fText: String? = TODO("Initialize fText")

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) const override {
   *         options->fSupportBilerpFromGlyphAtlas = true;
   *     }
   * ```
   */
  protected override fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
   *         SkFont font(fTypeface);
   *         size_t txtLen = strlen(fText);
   *         int glyphCount = font.countText(fText, txtLen, SkTextEncoding::kUTF8);
   *
   *         fGlyphs.append(glyphCount);
   *         font.textToGlyphs(fText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("slug"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1000, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         sk_sp<SkTextBlob> blob(this->makeBlob());
   *         SkPaint p;
   *         p.setAntiAlias(true);
   *         canvas->clipIRect(SkIRect::MakeSize(this->getISize()).makeInset(40, 50));
   *         canvas->scale(1.3f, 1.3f);
   *         sk_sp<sktext::gpu::Slug> slug = sktext::gpu::Slug::ConvertBlob(canvas, *blob, {10, 10}, p);
   *         if (slug == nullptr) {
   *             return;
   *         }
   *         canvas->translate(0.5, 0.5);
   *         canvas->translate(30, 30);
   *         canvas->drawTextBlob(blob, 10, 10, p);
   *         canvas->translate(370, 0);
   *         slug->draw(canvas, p);
   *         for (float scale = 1.5; scale < 4; scale += 0.5) {
   *             canvas->translate(-370, 20 * scale);
   *             canvas->save();
   *             canvas->scale(scale, scale);
   *             canvas->rotate(5);
   *             canvas->drawTextBlob(blob, 10, 10, p);
   *             canvas->restore();
   *             canvas->translate(370, 0);
   *             canvas->save();
   *             canvas->scale(scale, scale);
   *             canvas->rotate(5);
   *
   *             slug->draw(canvas, p);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> makeBlob() {
   *         SkTextBlobBuilder builder;
   *
   *         SkFont font;
   *         font.setSubpixel(true);
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setTypeface(fTypeface);
   *         font.setSize(16);
   *
   *         const SkTextBlobBuilder::RunBuffer& buf = builder.allocRun(font, fGlyphs.size(), 0, 0);
   *         memcpy(buf.glyphs, fGlyphs.begin(), fGlyphs.size() * sizeof(uint16_t));
   *         return builder.make();
   *     }
   * ```
   */
  private fun makeBlob(): SkSp<SkTextBlob> {
    TODO("Implement makeBlob")
  }
}
