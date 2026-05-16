package org.skia.tests

import kotlin.String
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class TextBlobGM : public skiagm::GM {
 * public:
 *     TextBlobGM(const char* txt)
 *         : fText(txt) {
 *     }
 *
 * protected:
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
 *     SkString getName() const override { return SkString("textblob"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         for (unsigned b = 0; b < std::size(blobConfigs); ++b) {
 *             sk_sp<SkTextBlob> blob(this->makeBlob(b));
 *
 *             SkPaint p;
 *             p.setAntiAlias(true);
 *             SkPoint offset = SkPoint::Make(SkIntToScalar(10 + 300 * (b % 2)),
 *                                            SkIntToScalar(20 + 150 * (b / 2)));
 *
 *             canvas->drawTextBlob(blob, offset.x(), offset.y(), p);
 *
 *             p.setColor(SK_ColorBLUE);
 *             p.setStyle(SkPaint::kStroke_Style);
 *             SkRect box = blob->bounds();
 *             box.offset(offset);
 *             p.setAntiAlias(false);
 *             canvas->drawRect(box, p);
 *
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkTextBlob> makeBlob(unsigned blobIndex) {
 *         SkTextBlobBuilder builder;
 *
 *         SkFont font;
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setTypeface(fTypeface);
 *
 *         for (unsigned l = 0; l < std::size(blobConfigs[blobIndex]); ++l) {
 *             unsigned currentGlyph = 0;
 *
 *             for (unsigned c = 0; c < std::size(blobConfigs[blobIndex][l]); ++c) {
 *                 const BlobCfg* cfg = &blobConfigs[blobIndex][l][c];
 *                 unsigned count = cfg->count;
 *
 *                 if (count > fGlyphs.size() - currentGlyph) {
 *                     count = fGlyphs.size() - currentGlyph;
 *                 }
 *                 if (0 == count) {
 *                     break;
 *                 }
 *
 *                 font.setSize(kFontSize * cfg->scale);
 *                 const SkScalar advanceX = font.getSize() * 0.85f;
 *                 const SkScalar advanceY = font.getSize() * 1.5f;
 *
 *                 SkPoint offset = SkPoint::Make(currentGlyph * advanceX + c * advanceX,
 *                                                advanceY * l);
 *                 switch (cfg->pos) {
 *                 case kDefault_Pos: {
 *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRun(font, count,
 *                                                                                offset.x(),
 *                                                                                offset.y());
 *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
 *                 } break;
 *                 case kScalar_Pos: {
 *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRunPosH(font, count,
 *                                                                                    offset.y());
 *                     SkTDArray<SkScalar> pos;
 *                     for (unsigned i = 0; i < count; ++i) {
 *                         *pos.append() = offset.x() + i * advanceX;
 *                     }
 *
 *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
 *                     memcpy(buf.pos, pos.begin(), count * sizeof(SkScalar));
 *                 } break;
 *                 case kPoint_Pos: {
 *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRunPos(font, count);
 *
 *                     SkTDArray<SkScalar> pos;
 *                     for (unsigned i = 0; i < count; ++i) {
 *                         *pos.append() = offset.x() + i * advanceX;
 *                         *pos.append() = offset.y() + i * (advanceY / count);
 *                     }
 *
 *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
 *                     memcpy(buf.pos, pos.begin(), count * sizeof(SkScalar) * 2);
 *                 } break;
 *                 default:
 *                     SK_ABORT("unhandled pos value");
 *                 }
 *
 *                 currentGlyph += count;
 *             }
 *         }
 *
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
public open class TextBlobGM public constructor(
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
   * SkString getName() const override { return SkString("textblob"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         for (unsigned b = 0; b < std::size(blobConfigs); ++b) {
   *             sk_sp<SkTextBlob> blob(this->makeBlob(b));
   *
   *             SkPaint p;
   *             p.setAntiAlias(true);
   *             SkPoint offset = SkPoint::Make(SkIntToScalar(10 + 300 * (b % 2)),
   *                                            SkIntToScalar(20 + 150 * (b / 2)));
   *
   *             canvas->drawTextBlob(blob, offset.x(), offset.y(), p);
   *
   *             p.setColor(SK_ColorBLUE);
   *             p.setStyle(SkPaint::kStroke_Style);
   *             SkRect box = blob->bounds();
   *             box.offset(offset);
   *             p.setAntiAlias(false);
   *             canvas->drawRect(box, p);
   *
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
   * sk_sp<SkTextBlob> makeBlob(unsigned blobIndex) {
   *         SkTextBlobBuilder builder;
   *
   *         SkFont font;
   *         font.setSubpixel(true);
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setTypeface(fTypeface);
   *
   *         for (unsigned l = 0; l < std::size(blobConfigs[blobIndex]); ++l) {
   *             unsigned currentGlyph = 0;
   *
   *             for (unsigned c = 0; c < std::size(blobConfigs[blobIndex][l]); ++c) {
   *                 const BlobCfg* cfg = &blobConfigs[blobIndex][l][c];
   *                 unsigned count = cfg->count;
   *
   *                 if (count > fGlyphs.size() - currentGlyph) {
   *                     count = fGlyphs.size() - currentGlyph;
   *                 }
   *                 if (0 == count) {
   *                     break;
   *                 }
   *
   *                 font.setSize(kFontSize * cfg->scale);
   *                 const SkScalar advanceX = font.getSize() * 0.85f;
   *                 const SkScalar advanceY = font.getSize() * 1.5f;
   *
   *                 SkPoint offset = SkPoint::Make(currentGlyph * advanceX + c * advanceX,
   *                                                advanceY * l);
   *                 switch (cfg->pos) {
   *                 case kDefault_Pos: {
   *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRun(font, count,
   *                                                                                offset.x(),
   *                                                                                offset.y());
   *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
   *                 } break;
   *                 case kScalar_Pos: {
   *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRunPosH(font, count,
   *                                                                                    offset.y());
   *                     SkTDArray<SkScalar> pos;
   *                     for (unsigned i = 0; i < count; ++i) {
   *                         *pos.append() = offset.x() + i * advanceX;
   *                     }
   *
   *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
   *                     memcpy(buf.pos, pos.begin(), count * sizeof(SkScalar));
   *                 } break;
   *                 case kPoint_Pos: {
   *                     const SkTextBlobBuilder::RunBuffer& buf = builder.allocRunPos(font, count);
   *
   *                     SkTDArray<SkScalar> pos;
   *                     for (unsigned i = 0; i < count; ++i) {
   *                         *pos.append() = offset.x() + i * advanceX;
   *                         *pos.append() = offset.y() + i * (advanceY / count);
   *                     }
   *
   *                     memcpy(buf.glyphs, fGlyphs.begin() + currentGlyph, count * sizeof(uint16_t));
   *                     memcpy(buf.pos, pos.begin(), count * sizeof(SkScalar) * 2);
   *                 } break;
   *                 default:
   *                     SK_ABORT("unhandled pos value");
   *                 }
   *
   *                 currentGlyph += count;
   *             }
   *         }
   *
   *         return builder.make();
   *     }
   * ```
   */
  private fun makeBlob(blobIndex: UInt): SkSp<SkTextBlob> {
    TODO("Implement makeBlob")
  }
}
