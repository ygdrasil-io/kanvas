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
 * class ShapeThenWrap : public ShaperHarfBuzz {
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
public open class ShapeThenWrap : ShaperHarfBuzz() {
  /**
   * C++ original:
   * ```cpp
   * void ShapeThenWrap::wrap(char const * const utf8, size_t utf8Bytes,
   *                          const BiDiRunIterator& bidi,
   *                          const LanguageRunIterator& language,
   *                          const ScriptRunIterator& script,
   *                          const FontRunIterator& font,
   *                          RunIteratorQueue& runSegmenter,
   *                          const Feature* features, size_t featuresSize,
   *                          SkScalar width,
   *                          RunHandler* handler) const
   * {
   *     TArray<ShapedRun> runs;
   * {
   *     SkString currentLanguage;
   *     SkUnicodeBreak lineBreakIterator;
   *     SkUnicodeBreak graphemeBreakIterator;
   *     bool needIteratorInit = true;
   *     const char* utf8Start = nullptr;
   *     const char* utf8End = utf8;
   *     while (runSegmenter.advanceRuns()) {
   *         utf8Start = utf8End;
   *         utf8End = utf8 + runSegmenter.endOfCurrentRun();
   *
   *         runs.emplace_back(shape(utf8, utf8Bytes,
   *                                 utf8Start, utf8End,
   *                                 bidi, language, script, font,
   *                                 features, featuresSize));
   *         ShapedRun& run = runs.back();
   *
   *         if (needIteratorInit || !currentLanguage.equals(language.currentLanguage())) {
   *             currentLanguage = language.currentLanguage();
   *             lineBreakIterator = fUnicode->makeBreakIterator(currentLanguage.c_str(),
   *                                                             SkUnicode::BreakType::kLines);
   *             if (!lineBreakIterator) {
   *                 return;
   *             }
   *             graphemeBreakIterator = fUnicode->makeBreakIterator(currentLanguage.c_str(),
   *                                                                 SkUnicode::BreakType::kGraphemes);
   *             if (!graphemeBreakIterator) {
   *                 return;
   *             }
   *             needIteratorInit = false;
   *         }
   *         size_t utf8runLength = utf8End - utf8Start;
   *         if (!lineBreakIterator->setText(utf8Start, utf8runLength)) {
   *             return;
   *         }
   *         if (!graphemeBreakIterator->setText(utf8Start, utf8runLength)) {
   *             return;
   *         }
   *
   *         uint32_t previousCluster = 0xFFFFFFFF;
   *         for (size_t i = 0; i < run.fNumGlyphs; ++i) {
   *             ShapedGlyph& glyph = run.fGlyphs[i];
   *             int32_t glyphCluster = glyph.fCluster;
   *
   *             int32_t lineBreakIteratorCurrent = lineBreakIterator->current();
   *             while (!lineBreakIterator->isDone() && lineBreakIteratorCurrent < glyphCluster)
   *             {
   *                 lineBreakIteratorCurrent = lineBreakIterator->next();
   *             }
   *             glyph.fMayLineBreakBefore = glyph.fCluster != previousCluster &&
   *                                         lineBreakIteratorCurrent == glyphCluster;
   *
   *             int32_t graphemeBreakIteratorCurrent = graphemeBreakIterator->current();
   *             while (!graphemeBreakIterator->isDone() && graphemeBreakIteratorCurrent < glyphCluster)
   *             {
   *                 graphemeBreakIteratorCurrent = graphemeBreakIterator->next();
   *             }
   *             glyph.fGraphemeBreakBefore = glyph.fCluster != previousCluster &&
   *                                          graphemeBreakIteratorCurrent == glyphCluster;
   *
   *             previousCluster = glyph.fCluster;
   *         }
   *     }
   * }
   *
   * // Iterate over the glyphs in logical order to find potential line lengths.
   * {
   *     /** The position of the beginning of the line. */
   *     ShapedRunGlyphIterator beginning(runs);
   *
   *     /** The position of the candidate line break. */
   *     ShapedRunGlyphIterator candidateLineBreak(runs);
   *     SkScalar candidateLineBreakWidth = 0;
   *
   *     /** The position of the candidate grapheme break. */
   *     ShapedRunGlyphIterator candidateGraphemeBreak(runs);
   *     SkScalar candidateGraphemeBreakWidth = 0;
   *
   *     /** The position of the current location. */
   *     ShapedRunGlyphIterator current(runs);
   *     SkScalar currentWidth = 0;
   *     while (ShapedGlyph* glyph = current.current()) {
   *         // 'Break' at graphemes until a line boundary, then only at line boundaries.
   *         // Only break at graphemes if no line boundary is valid.
   *         if (current != beginning) {
   *             if (glyph->fGraphemeBreakBefore || glyph->fMayLineBreakBefore) {
   *                 // TODO: preserve line breaks <= grapheme breaks
   *                 // and prevent line breaks inside graphemes
   *                 candidateGraphemeBreak = current;
   *                 candidateGraphemeBreakWidth = currentWidth;
   *                 if (glyph->fMayLineBreakBefore) {
   *                     candidateLineBreak = current;
   *                     candidateLineBreakWidth = currentWidth;
   *                 }
   *             }
   *         }
   *
   *         SkScalar glyphWidth = glyph->fAdvance.fX;
   *         // Break when overwidth, the glyph has a visual representation, and some space is used.
   *         if (width < currentWidth + glyphWidth && glyph->fHasVisual && candidateGraphemeBreakWidth > 0){
   *             if (candidateLineBreak != beginning) {
   *                 beginning = candidateLineBreak;
   *                 currentWidth -= candidateLineBreakWidth;
   *                 candidateGraphemeBreakWidth -= candidateLineBreakWidth;
   *                 candidateLineBreakWidth = 0;
   *             } else if (candidateGraphemeBreak != beginning) {
   *                 beginning = candidateGraphemeBreak;
   *                 candidateLineBreak = beginning;
   *                 currentWidth -= candidateGraphemeBreakWidth;
   *                 candidateGraphemeBreakWidth = 0;
   *                 candidateLineBreakWidth = 0;
   *             } else {
   *                 SK_ABORT("");
   *             }
   *
   *             if (width < currentWidth) {
   *                 if (width < candidateGraphemeBreakWidth) {
   *                     candidateGraphemeBreak = candidateLineBreak;
   *                     candidateGraphemeBreakWidth = candidateLineBreakWidth;
   *                 }
   *                 current = candidateGraphemeBreak;
   *                 currentWidth = candidateGraphemeBreakWidth;
   *             }
   *
   *             glyph = beginning.current();
   *             if (glyph) {
   *                 glyph->fMustLineBreakBefore = true;
   *             }
   *
   *         } else {
   *             current.next();
   *             currentWidth += glyphWidth;
   *         }
   *     }
   * }
   *
   * // Reorder the runs and glyphs per line and write them out.
   * {
   *     ShapedRunGlyphIterator previousBreak(runs);
   *     ShapedRunGlyphIterator glyphIterator(runs);
   *     int previousRunIndex = -1;
   *     while (glyphIterator.current()) {
   *         const ShapedRunGlyphIterator current = glyphIterator;
   *         ShapedGlyph* nextGlyph = glyphIterator.next();
   *
   *         if (previousRunIndex != current.fRunIndex) {
   *             SkFontMetrics metrics;
   *             runs[current.fRunIndex].fFont.getMetrics(&metrics);
   *             previousRunIndex = current.fRunIndex;
   *         }
   *
   *         // Nothing can be written until the baseline is known.
   *         if (!(nextGlyph == nullptr || nextGlyph->fMustLineBreakBefore)) {
   *             continue;
   *         }
   *
   *         int numRuns = current.fRunIndex - previousBreak.fRunIndex + 1;
   *         AutoSTMalloc<4, SkBidiIterator::Level> runLevels(numRuns);
   *         for (int i = 0; i < numRuns; ++i) {
   *             runLevels[i] = runs[previousBreak.fRunIndex + i].fLevel;
   *         }
   *         AutoSTMalloc<4, int32_t> logicalFromVisual(numRuns);
   *         fUnicode->reorderVisual(runLevels, numRuns, logicalFromVisual);
   *
   *         // step through the runs in reverse visual order and the glyphs in reverse logical order
   *         // until a visible glyph is found and force them to the end of the visual line.
   *
   *         handler->beginLine();
   *
   *         struct SubRun { const ShapedRun& run; size_t startGlyphIndex; size_t endGlyphIndex; };
   *         auto makeSubRun = [&runs, &previousBreak, &current, &logicalFromVisual](size_t visualIndex){
   *             int logicalIndex = previousBreak.fRunIndex + logicalFromVisual[visualIndex];
   *             const auto& run = runs[logicalIndex];
   *             size_t startGlyphIndex = (logicalIndex == previousBreak.fRunIndex)
   *                                    ? previousBreak.fGlyphIndex
   *                                    : 0;
   *             size_t endGlyphIndex = (logicalIndex == current.fRunIndex)
   *                                  ? current.fGlyphIndex + 1
   *                                  : run.fNumGlyphs;
   *             return SubRun{ run, startGlyphIndex, endGlyphIndex };
   *         };
   *         auto makeRunInfo = [](const SubRun& sub) {
   *             uint32_t startUtf8 = sub.run.fGlyphs[sub.startGlyphIndex].fCluster;
   *             uint32_t endUtf8 = (sub.endGlyphIndex < sub.run.fNumGlyphs)
   *                              ? sub.run.fGlyphs[sub.endGlyphIndex].fCluster
   *                              : sub.run.fUtf8Range.end();
   *
   *             SkVector advance = SkVector::Make(0, 0);
   *             for (size_t i = sub.startGlyphIndex; i < sub.endGlyphIndex; ++i) {
   *                 advance += sub.run.fGlyphs[i].fAdvance;
   *             }
   *
   *             return RunHandler::RunInfo{
   *                 sub.run.fFont,
   *                 sub.run.fLevel,
   *                 sub.run.fScript,
   *                 sub.run.fLanguage,
   *                 advance,
   *                 sub.endGlyphIndex - sub.startGlyphIndex,
   *                 RunHandler::Range(startUtf8, endUtf8 - startUtf8)
   *             };
   *         };
   *
   *         for (int i = 0; i < numRuns; ++i) {
   *             handler->runInfo(makeRunInfo(makeSubRun(i)));
   *         }
   *         handler->commitRunInfo();
   *         for (int i = 0; i < numRuns; ++i) {
   *             SubRun sub = makeSubRun(i);
   *             append(handler, makeRunInfo(sub), sub.run, sub.startGlyphIndex, sub.endGlyphIndex);
   *         }
   *
   *         handler->commitLine();
   *
   *         previousRunIndex = -1;
   *         previousBreak = glyphIterator;
   *     }
   * }
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
