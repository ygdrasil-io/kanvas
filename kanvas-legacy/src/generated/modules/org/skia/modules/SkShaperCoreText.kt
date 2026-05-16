package org.skia.modules

import kotlin.Boolean
import kotlin.String
import kotlin.ULong
import org.skia.`external`.Feature
import org.skia.foundation.SkFont
import org.skia.math.SkScalar
import undefined.BiDiRunIterator
import undefined.FontRunIterator
import undefined.LanguageRunIterator

/**
 * C++ original:
 * ```cpp
 * class SkShaper_CoreText : public SkShaper {
 * public:
 *     SkShaper_CoreText() {}
 * private:
 * #if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                const SkFont& srcFont,
 *                bool leftToRight,
 *                SkScalar width,
 *                RunHandler*) const override;
 *
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                SkScalar width,
 *                RunHandler*) const override;
 * #endif
 *
 *     void shape(const char* utf8, size_t utf8Bytes,
 *                FontRunIterator&,
 *                BiDiRunIterator&,
 *                ScriptRunIterator&,
 *                LanguageRunIterator&,
 *                const Feature*, size_t featureSize,
 *                SkScalar width,
 *                RunHandler*) const override;
 * }
 * ```
 */
public open class SkShaperCoreText public constructor() : SkShaper() {
  /**
   * C++ original:
   * ```cpp
   * void SkShaper_CoreText::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               const SkFont& font,
   *                               bool,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     std::unique_ptr<FontRunIterator> fontRuns(
   *             MakeFontMgrRunIterator(utf8, utf8Bytes, font, nullptr));
   *     if (!fontRuns) {
   *         return;
   *     }
   *     // bidi, script, and lang are all unused so we can construct them with empty data.
   *     TrivialBiDiRunIterator bidi{0, 0};
   *     TrivialScriptRunIterator script{0, 0};
   *     TrivialLanguageRunIterator lang{nullptr, 0};
   *     return this->shape(utf8, utf8Bytes, *fontRuns, bidi, script, lang, nullptr, 0, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    srcFont: SkFont,
    leftToRight: Boolean,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaper_CoreText::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               FontRunIterator& font,
   *                               BiDiRunIterator& bidi,
   *                               ScriptRunIterator& script,
   *                               LanguageRunIterator& lang,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     return this->shape(utf8, utf8Bytes, font, bidi, script, lang, nullptr, 0, width, handler);
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    font: FontRunIterator,
    bidi: BiDiRunIterator,
    script: ScriptRunIterator,
    lang: LanguageRunIterator,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaper_CoreText::shape(const char* utf8,
   *                               size_t utf8Bytes,
   *                               FontRunIterator& fontRuns,
   *                               BiDiRunIterator&,
   *                               ScriptRunIterator&,
   *                               LanguageRunIterator&,
   *                               const Feature*,
   *                               size_t,
   *                               SkScalar width,
   *                               RunHandler* handler) const {
   *     SkFont font;
   *     if (!fontRuns.atEnd()) {
   *         fontRuns.consume();
   *         font = fontRuns.currentFont();
   *     }
   *
   *     SkUniqueCFRef<CFStringRef> textString(
   *             CFStringCreateWithBytes(kCFAllocatorDefault, (const uint8_t*)utf8, utf8Bytes,
   *                                     kCFStringEncodingUTF8, false));
   *
   *     UTF16ToUTF8IndicesMap utf8IndicesMap;
   *     if (!utf8IndicesMap.setUTF8(utf8, utf8Bytes)) {
   *         return;
   *     }
   *
   *     SkUniqueCFRef<CTFontRef> ctfont = create_ctfont_from_font(font);
   *     if (!ctfont) {
   *         return;
   *     }
   *
   *     SkUniqueCFRef<CFMutableDictionaryRef> attr(
   *             CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
   *                                       &kCFTypeDictionaryKeyCallBacks,
   *                                       &kCFTypeDictionaryValueCallBacks));
   *     CFDictionaryAddValue(attr.get(), kCTFontAttributeName, ctfont.get());
   *     if ((false)) {
   *         // trying to see what these affect
   *         dict_add_double(attr.get(), kCTTracking_AttributeName, 1);
   *         dict_add_double(attr.get(), kCTKernAttributeName, 0.0);
   *     }
   *
   *     SkUniqueCFRef<CFAttributedStringRef> attrString(
   *             CFAttributedStringCreate(kCFAllocatorDefault, textString.get(), attr.get()));
   *
   *     SkUniqueCFRef<CTTypesetterRef> typesetter(
   *             CTTypesetterCreateWithAttributedString(attrString.get()));
   *
   *     // We have to compute RunInfos in a loop, and then reuse them in a 2nd loop,
   *     // so we store them in an array (we reuse the array's storage for each line).
   *     std::vector<SkFont> fontStorage;
   *     std::vector<SkShaper::RunHandler::RunInfo> infos;
   *
   *     LineBreakIter iter(typesetter.get(), width);
   *     while (SkUniqueCFRef<CTLineRef> line = iter.nextLine()) {
   *         CFArrayRef run_array = CTLineGetGlyphRuns(line.get());
   *         CFIndex runCount = CFArrayGetCount(run_array);
   *         if (runCount == 0) {
   *             continue;
   *         }
   *         handler->beginLine();
   *         fontStorage.clear();
   *         fontStorage.reserve(runCount); // ensure the refs won't get invalidated
   *         infos.clear();
   *         for (CFIndex j = 0; j < runCount; ++j) {
   *             CTRunRef run = (CTRunRef)CFArrayGetValueAtIndex(run_array, j);
   *             CFIndex runGlyphs = CTRunGetGlyphCount(run);
   *
   *             SkASSERT(sizeof(CGGlyph) == sizeof(uint16_t));
   *
   *             AutoSTArray<4096, CGSize> advances(runGlyphs);
   *             CTRunGetAdvances(run, {0, runGlyphs}, advances.data());
   *             SkScalar adv = 0;
   *             for (CFIndex k = 0; k < runGlyphs; ++k) {
   *                 adv += advances[k].width;
   *             }
   *
   *             CFRange cfRange = CTRunGetStringRange(run);
   *             auto range = utf8IndicesMap.mapRange(cfRange.location, cfRange.length);
   *
   *             fontStorage.push_back(run_to_font(run, font));
   *             infos.push_back(SkShaper::RunHandler::RunInfo{
   *                 fontStorage.back(), // info just stores a ref to the font
   *                 0,                  // TODO: need fBidiLevel
   *                 0,                  // TODO: need fScript
   *                 "",                 // TODO: need fLanguage
   *                 {adv, 0},
   *                 (size_t)runGlyphs,
   *                 {range.first, range.second},
   *             });
   *             handler->runInfo(infos.back());
   *         }
   *         handler->commitRunInfo();
   *
   *         // Now loop through again and fill in the buffers
   *         SkScalar lineAdvance = 0;
   *         for (CFIndex j = 0; j < runCount; ++j) {
   *             const auto& info = infos[j];
   *             auto buffer = handler->runBuffer(info);
   *
   *             CTRunRef run = (CTRunRef)CFArrayGetValueAtIndex(run_array, j);
   *             CFIndex runGlyphs = info.glyphCount;
   *             SkASSERT(CTRunGetGlyphCount(run) == (CFIndex)info.glyphCount);
   *
   *             CTRunGetGlyphs(run, {0, runGlyphs}, buffer.glyphs);
   *
   *             AutoSTArray<4096, CGPoint> positions(runGlyphs);
   *             CTRunGetPositions(run, {0, runGlyphs}, positions.data());
   *             AutoSTArray<4096, CFIndex> indices;
   *             if (buffer.clusters) {
   *                 indices.reset(runGlyphs);
   *                 CTRunGetStringIndices(run, {0, runGlyphs}, indices.data());
   *             }
   *
   *             for (CFIndex k = 0; k < runGlyphs; ++k) {
   *                 buffer.positions[k] = {
   *                     buffer.point.fX + SkScalarFromCGFloat(positions[k].x) - lineAdvance,
   *                     buffer.point.fY,
   *                 };
   *                 if (buffer.offsets) {
   *                     buffer.offsets[k] = {0, 0}; // offset relative to the origin for this glyph
   *                 }
   *                 if (buffer.clusters) {
   *                     buffer.clusters[k] = utf8IndicesMap.mapIndex(indices[k]);
   *                 }
   *             }
   *             handler->commitRunBuffer(info);
   *             lineAdvance += info.fAdvance.fX;
   *         }
   *         handler->commitLine();
   *     }
   * }
   * ```
   */
  public override fun shape(
    utf8: String?,
    utf8Bytes: ULong,
    fontRuns: FontRunIterator,
    param3: BiDiRunIterator,
    param4: ScriptRunIterator,
    param5: LanguageRunIterator,
    param6: Feature?,
    featureSize: ULong,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement shape")
  }
}
