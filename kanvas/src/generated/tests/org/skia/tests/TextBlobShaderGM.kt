package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class TextBlobShaderGM : public skiagm::GM {
 * public:
 *     TextBlobShaderGM() {}
 *
 * private:
 *     void onOnceBeforeDraw() override {
 *         {
 *             SkFont      font = ToolUtils::DefaultPortableFont();
 *             const char* txt = "Blobber";
 *             size_t txtLen = strlen(txt);
 *             fGlyphs.append(font.countText(txt, txtLen, SkTextEncoding::kUTF8));
 *             font.textToGlyphs(txt, txtLen, SkTextEncoding::kUTF8, fGlyphs);
 *         }
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setSize(30);
 *
 *         SkTextBlobBuilder builder;
 *         int glyphCount = fGlyphs.size();
 *         const SkTextBlobBuilder::RunBuffer* run;
 *
 *         run = &builder.allocRun(font, glyphCount, 10, 10, nullptr);
 *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
 *
 *         run = &builder.allocRunPosH(font, glyphCount,  80, nullptr);
 *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
 *         for (int i = 0; i < glyphCount; ++i) {
 *             run->pos[i] = font.getSize() * i * .75f;
 *         }
 *
 *         run = &builder.allocRunPos(font, glyphCount, nullptr);
 *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
 *         for (int i = 0; i < glyphCount; ++i) {
 *             run->pos[i * 2] = font.getSize() * i * .75f;
 *             run->pos[i * 2 + 1] = 150 + 5 * sinf((float)i * 8 / glyphCount);
 *         }
 *
 *         fBlob = builder.make();
 *
 *         const SkColor4f colors[] = {SkColors::kRed, SkColors::kGreen};
 *
 *         SkScalar pos[std::size(colors)];
 *         for (unsigned i = 0; i < std::size(pos); ++i) {
 *             pos[i] = (float)i / (std::size(pos) - 1);
 *         }
 *
 *         SkISize sz = this->getISize();
 *         fShader = SkShaders::RadialGradient(SkPoint::Make(SkIntToScalar(sz.width() / 2),
 *                                                           SkIntToScalar(sz.height() / 2)),
 *                                                sz.width() * .66f,
 *                                                {{colors, pos, SkTileMode::kRepeat}, {}});
 *     }
 *
 *     SkString getName() const override { return SkString("textblobshader"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint p;
 *         p.setAntiAlias(true);
 *         p.setStyle(SkPaint::kFill_Style);
 *         p.setShader(fShader);
 *
 *         SkISize sz = this->getISize();
 *         constexpr int kXCount = 4;
 *         constexpr int kYCount = 3;
 *         for (int i = 0; i < kXCount; ++i) {
 *             for (int j = 0; j < kYCount; ++j) {
 *                 canvas->drawTextBlob(fBlob,
 *                                      SkIntToScalar(i * sz.width() / kXCount),
 *                                      SkIntToScalar(j * sz.height() / kYCount),
 *                                      p);
 *             }
 *         }
 *     }
 *
 *     SkTDArray<SkGlyphID> fGlyphs;
 *     sk_sp<SkTextBlob>   fBlob;
 *     sk_sp<SkShader>     fShader;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TextBlobShaderGM public constructor() : GM() {
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
   * sk_sp<SkTextBlob>   fBlob
   * ```
   */
  private var fBlob: SkSp<SkTextBlob> = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>     fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         {
   *             SkFont      font = ToolUtils::DefaultPortableFont();
   *             const char* txt = "Blobber";
   *             size_t txtLen = strlen(txt);
   *             fGlyphs.append(font.countText(txt, txtLen, SkTextEncoding::kUTF8));
   *             font.textToGlyphs(txt, txtLen, SkTextEncoding::kUTF8, fGlyphs);
   *         }
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setSubpixel(true);
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setSize(30);
   *
   *         SkTextBlobBuilder builder;
   *         int glyphCount = fGlyphs.size();
   *         const SkTextBlobBuilder::RunBuffer* run;
   *
   *         run = &builder.allocRun(font, glyphCount, 10, 10, nullptr);
   *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
   *
   *         run = &builder.allocRunPosH(font, glyphCount,  80, nullptr);
   *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
   *         for (int i = 0; i < glyphCount; ++i) {
   *             run->pos[i] = font.getSize() * i * .75f;
   *         }
   *
   *         run = &builder.allocRunPos(font, glyphCount, nullptr);
   *         memcpy(run->glyphs, fGlyphs.begin(), glyphCount * sizeof(uint16_t));
   *         for (int i = 0; i < glyphCount; ++i) {
   *             run->pos[i * 2] = font.getSize() * i * .75f;
   *             run->pos[i * 2 + 1] = 150 + 5 * sinf((float)i * 8 / glyphCount);
   *         }
   *
   *         fBlob = builder.make();
   *
   *         const SkColor4f colors[] = {SkColors::kRed, SkColors::kGreen};
   *
   *         SkScalar pos[std::size(colors)];
   *         for (unsigned i = 0; i < std::size(pos); ++i) {
   *             pos[i] = (float)i / (std::size(pos) - 1);
   *         }
   *
   *         SkISize sz = this->getISize();
   *         fShader = SkShaders::RadialGradient(SkPoint::Make(SkIntToScalar(sz.width() / 2),
   *                                                           SkIntToScalar(sz.height() / 2)),
   *                                                sz.width() * .66f,
   *                                                {{colors, pos, SkTileMode::kRepeat}, {}});
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("textblobshader"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint p;
   *         p.setAntiAlias(true);
   *         p.setStyle(SkPaint::kFill_Style);
   *         p.setShader(fShader);
   *
   *         SkISize sz = this->getISize();
   *         constexpr int kXCount = 4;
   *         constexpr int kYCount = 3;
   *         for (int i = 0; i < kXCount; ++i) {
   *             for (int j = 0; j < kYCount; ++j) {
   *                 canvas->drawTextBlob(fBlob,
   *                                      SkIntToScalar(i * sz.width() / kXCount),
   *                                      SkIntToScalar(j * sz.height() / kYCount),
   *                                      p);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
