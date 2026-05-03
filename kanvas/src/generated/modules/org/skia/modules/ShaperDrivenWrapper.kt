package org.skia.modules

import kotlin.String
import kotlin.ULong
import org.skia.`external`.Feature
import org.skia.math.SkScalar
import undefined.BiDiRunIterator
import undefined.FontRunIterator
import undefined.LanguageRunIterator

/**
 * C++ original:
 * ```cpp
 * class ShaperDrivenWrapper : public ShaperHarfBuzz {
 * public:
 *     using ShaperHarfBuzz::ShaperHarfBuzz;
 * private:
 *     void wrap(char const * utf8, size_t utf8Bytes,
 *               const BiDiRunIterator&,
 *               const LanguageRunIterator&,
 *               const ScriptRunIterator&,
 *               const FontRunIterator&,
 *               RunIteratorQueue& runSegmenter,
 *               const Feature*, size_t featuresSize,
 *               SkScalar width,
 *               RunHandler*) const override;
 * }
 * ```
 */
public open class ShaperDrivenWrapper : ShaperHarfBuzz() {
  /**
   * C++ original:
   * ```cpp
   * void ShaperDrivenWrapper::wrap(char const * const utf8, size_t utf8Bytes,
   *                                const BiDiRunIterator& bidi,
   *                                const LanguageRunIterator& language,
   *                                const ScriptRunIterator& script,
   *                                const FontRunIterator& font,
   *                                RunIteratorQueue& runSegmenter,
   *                                const Feature* features, size_t featuresSize,
   *                                SkScalar width,
   *                                RunHandler* handler) const
   * {
   *     ShapedLine line;
   *
   *     const char* utf8Start = nullptr;
   *     const char* utf8End = utf8;
   *     SkUnicodeBreak lineBreakIterator;
   *     SkString currentLanguage;
   *     while (runSegmenter.advanceRuns()) {  // For each item
   *         utf8Start = utf8End;
   *         utf8End = utf8 + runSegmenter.endOfCurrentRun();
   *
   *         ShapedRun model(RunHandler::Range(), SkFont(), 0, 0, {}, nullptr, 0);
   *         bool modelNeedsRegenerated = true;
   *         int modelGlyphOffset = 0;
   *
   *         struct TextProps {
   *             int glyphLen = 0;
   *             SkVector advance = {0, 0};
   *         };
   *         // map from character position to [safe to break, glyph position, advance]
   *         std::unique_ptr<TextProps[]> modelText;
   *         int modelTextOffset = 0;
   *         SkVector modelAdvanceOffset = {0, 0};
   *
   *         while (utf8Start < utf8End) {  // While there are still code points left in this item
   *             size_t utf8runLength = utf8End - utf8Start;
   *             if (modelNeedsRegenerated) {
   *                 model = shape(utf8, utf8Bytes,
   *                               utf8Start, utf8End,
   *                               bidi, language, script, font,
   *                               features, featuresSize);
   *                 modelGlyphOffset = 0;
   *
   *                 SkVector advance = {0, 0};
   *                 modelText = std::make_unique<TextProps[]>(utf8runLength + 1);
   *                 size_t modelStartCluster = utf8Start - utf8;
   *                 size_t previousCluster = 0;
   *                 for (size_t i = 0; i < model.fNumGlyphs; ++i) {
   *                     SkASSERT(modelStartCluster <= model.fGlyphs[i].fCluster);
   *                     SkASSERT(                     model.fGlyphs[i].fCluster < (size_t)(utf8End - utf8));
   *                     if (!model.fGlyphs[i].fUnsafeToBreak) {
   *                         // Store up to the first glyph in the cluster.
   *                         size_t currentCluster = model.fGlyphs[i].fCluster - modelStartCluster;
   *                         if (previousCluster != currentCluster) {
   *                             previousCluster  = currentCluster;
   *                             modelText[currentCluster].glyphLen = i;
   *                             modelText[currentCluster].advance = advance;
   *                         }
   *                     }
   *                     advance += model.fGlyphs[i].fAdvance;
   *                 }
   *                 // Assume it is always safe to break after the end of an item
   *                 modelText[utf8runLength].glyphLen = model.fNumGlyphs;
   *                 modelText[utf8runLength].advance = model.fAdvance;
   *                 modelTextOffset = 0;
   *                 modelAdvanceOffset = {0, 0};
   *                 modelNeedsRegenerated = false;
   *             }
   *
   *             // TODO: break iterator per item, but just reset position if needed?
   *             // Maybe break iterator with model?
   *             if (!lineBreakIterator || !currentLanguage.equals(language.currentLanguage())) {
   *                 currentLanguage = language.currentLanguage();
   *                 lineBreakIterator = fUnicode->makeBreakIterator(currentLanguage.c_str(),
   *                                                                 SkUnicode::BreakType::kLines);
   *                 if (!lineBreakIterator) {
   *                     return;
   *                 }
   *             }
   *             if (!lineBreakIterator->setText(utf8Start, utf8runLength)) {
   *                 return;
   *             }
   *             SkBreakIterator& breakIterator = *lineBreakIterator;
   *
   *             ShapedRun best(RunHandler::Range(), SkFont(), 0, 0, {}, nullptr, 0,
   *                            { SK_ScalarNegativeInfinity, SK_ScalarNegativeInfinity });
   *             bool bestIsInvalid = true;
   *             bool bestUsesModelForGlyphs = false;
   *             SkScalar widthLeft = width - line.fAdvance.fX;
   *
   *             for (int32_t breakIteratorCurrent = breakIterator.next();
   *                  !breakIterator.isDone();
   *                  breakIteratorCurrent = breakIterator.next())
   *             {
   *                 // TODO: if past a safe to break, future safe to break will be at least as long
   *
   *                 // TODO: adjust breakIteratorCurrent by ignorable whitespace
   *                 bool candidateUsesModelForGlyphs = false;
   *                 ShapedRun candidate = [&](const TextProps& props){
   *                     if (props.glyphLen) {
   *                         candidateUsesModelForGlyphs = true;
   *                         return ShapedRun(RunHandler::Range(utf8Start - utf8, breakIteratorCurrent),
   *                                          font.currentFont(), bidi.currentLevel(),
   *                                          script.currentScript(), language.currentLanguage(),
   *                                          std::unique_ptr<ShapedGlyph[]>(),
   *                                          props.glyphLen - modelGlyphOffset,
   *                                          props.advance - modelAdvanceOffset);
   *                     } else {
   *                         return shape(utf8, utf8Bytes,
   *                                      utf8Start, utf8Start + breakIteratorCurrent,
   *                                      bidi, language, script, font,
   *                                      features, featuresSize);
   *                     }
   *                 }(modelText[breakIteratorCurrent + modelTextOffset]);
   *                 auto score = [widthLeft](const ShapedRun& run) -> SkScalar {
   *                     if (run.fAdvance.fX < widthLeft) {
   *                         return run.fUtf8Range.size();
   *                     } else {
   *                         return widthLeft - run.fAdvance.fX;
   *                     }
   *                 };
   *                 if (bestIsInvalid || score(best) < score(candidate)) {
   *                     best = std::move(candidate);
   *                     bestIsInvalid = false;
   *                     bestUsesModelForGlyphs = candidateUsesModelForGlyphs;
   *                 }
   *             }
   *
   *             // If nothing fit (best score is negative) and the line is not empty
   *             if (width < line.fAdvance.fX + best.fAdvance.fX && !line.runs.empty()) {
   *                 emit(fUnicode.get(), line, handler);
   *                 line.runs.clear();
   *                 line.fAdvance = {0, 0};
   *             } else {
   *                 if (bestUsesModelForGlyphs) {
   *                     best.fGlyphs = std::make_unique<ShapedGlyph[]>(best.fNumGlyphs);
   *                     memcpy(best.fGlyphs.get(), model.fGlyphs.get() + modelGlyphOffset,
   *                            best.fNumGlyphs * sizeof(ShapedGlyph));
   *                     modelGlyphOffset += best.fNumGlyphs;
   *                     modelTextOffset += best.fUtf8Range.size();
   *                     modelAdvanceOffset += best.fAdvance;
   *                 } else {
   *                     modelNeedsRegenerated = true;
   *                 }
   *                 utf8Start += best.fUtf8Range.size();
   *                 line.fAdvance += best.fAdvance;
   *                 line.runs.emplace_back(std::move(best));
   *
   *                 // If item broken, emit line (prevent remainder from accidentally fitting)
   *                 if (utf8Start != utf8End) {
   *                     emit(fUnicode.get(), line, handler);
   *                     line.runs.clear();
   *                     line.fAdvance = {0, 0};
   *                 }
   *             }
   *         }
   *     }
   *     emit(fUnicode.get(), line, handler);
   * }
   * ```
   */
  public override fun wrap(
    utf8: String?,
    utf8Bytes: ULong,
    bidi: BiDiRunIterator,
    language: LanguageRunIterator,
    script: ScriptRunIterator,
    font: FontRunIterator,
    runSegmenter: RunIteratorQueue,
    features: Feature?,
    featuresSize: ULong,
    width: SkScalar,
    handler: RunHandler?,
  ) {
    TODO("Implement wrap")
  }
}
