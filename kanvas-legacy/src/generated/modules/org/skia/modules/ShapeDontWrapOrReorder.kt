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
 * class ShapeDontWrapOrReorder : public ShaperHarfBuzz {
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
public open class ShapeDontWrapOrReorder : ShaperHarfBuzz() {
  /**
   * C++ original:
   * ```cpp
   * void ShapeDontWrapOrReorder::wrap(char const * const utf8, size_t utf8Bytes,
   *                                   const BiDiRunIterator& bidi,
   *                                   const LanguageRunIterator& language,
   *                                   const ScriptRunIterator& script,
   *                                   const FontRunIterator& font,
   *                                   RunIteratorQueue& runSegmenter,
   *                                   const Feature* features, size_t featuresSize,
   *                                   SkScalar width,
   *                                   RunHandler* handler) const
   * {
   *     sk_ignore_unused_variable(width);
   *     TArray<ShapedRun> runs;
   *
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
   *     }
   *
   *     handler->beginLine();
   *     for (const auto& run : runs) {
   *         const RunHandler::RunInfo info = {
   *             run.fFont,
   *             run.fLevel,
   *             run.fScript,
   *             run.fLanguage,
   *             run.fAdvance,
   *             run.fNumGlyphs,
   *             run.fUtf8Range
   *         };
   *         handler->runInfo(info);
   *     }
   *     handler->commitRunInfo();
   *     for (const auto& run : runs) {
   *         const RunHandler::RunInfo info = {
   *             run.fFont,
   *             run.fLevel,
   *             run.fScript,
   *             run.fLanguage,
   *             run.fAdvance,
   *             run.fNumGlyphs,
   *             run.fUtf8Range
   *         };
   *         append(handler, info, run, 0, run.fNumGlyphs);
   *     }
   *     handler->commitLine();
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
