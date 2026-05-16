package org.skia.modules

import SkShapers.Factory
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.core.STArray64True
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.gpu.Buffer
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize
import org.skia.math.SkVector
import org.skia.utils.SkTextUtils
import undefined.RunInfo

/**
 * C++ original:
 * ```cpp
 * class ResultBuilder final : public SkShaper::RunHandler {
 * public:
 *     ResultBuilder(const Shaper::TextDesc& desc, const SkRect& box, const sk_sp<SkFontMgr>& fontmgr,
 *                   const sk_sp<SkShapers::Factory>& shapingFactory)
 *             : fDesc(desc)
 *             , fBox(box)
 *             , fHAlignFactor(HAlignFactor(fDesc.fHAlign))
 *             , fFont(fDesc.fTypeface, fDesc.fTextSize)
 *             , fFontMgr(fontmgr)
 *             , fShapingFactory(shapingFactory) {
 *         // If the shaper callback returns null, fallback to the primitive shaper.
 *         SkASSERT(fShapingFactory);
 *         fShaper = fShapingFactory->makeShaper(fFontMgr);
 *         if (!fShaper) {
 *             fShaper = SkShapers::Primitive::PrimitiveText();
 *             fShapingFactory = SkShapers::Primitive::Factory();
 *         }
 *         fFont.setHinting(SkFontHinting::kNone);
 *         fFont.setSubpixel(true);
 *         fFont.setLinearMetrics(true);
 *         fFont.setBaselineSnap(false);
 *         fFont.setEdging(SkFont::Edging::kAntiAlias);
 *     }
 *
 *     void beginLine() override {
 *         fLineGlyphs.reset(0);
 *         fLinePos.reset(0);
 *         fLineClusters.reset(0);
 *         fLineRuns.clear();
 *         fLineGlyphCount = 0;
 *
 *         fCurrentPosition = fOffset;
 *         fPendingLineAdvance  = { 0, 0 };
 *
 *         fLastLineDescent = 0;
 *     }
 *
 *     void runInfo(const RunInfo& info) override {
 *         fPendingLineAdvance += info.fAdvance;
 *
 *         SkFontMetrics metrics;
 *         info.fFont.getMetrics(&metrics);
 *         if (!fLineCount) {
 *             fFirstLineAscent = std::min(fFirstLineAscent, metrics.fAscent);
 *         }
 *         fLastLineDescent = std::max(fLastLineDescent, metrics.fDescent);
 *     }
 *
 *     void commitRunInfo() override {}
 *
 *     Buffer runBuffer(const RunInfo& info) override {
 *         const auto run_start_index = fLineGlyphCount;
 *         fLineGlyphCount += info.glyphCount;
 *
 *         fLineGlyphs.realloc(fLineGlyphCount);
 *         fLinePos.realloc(fLineGlyphCount);
 *         fLineClusters.realloc(fLineGlyphCount);
 *         fLineRuns.push_back({info.fFont, info.glyphCount});
 *
 *         SkVector alignmentOffset { fHAlignFactor * (fPendingLineAdvance.x() - fBox.width()), 0 };
 *
 *         return {
 *             fLineGlyphs.get()   + run_start_index,
 *             fLinePos.get()      + run_start_index,
 *             nullptr,
 *             fLineClusters.get() + run_start_index,
 *             fCurrentPosition + alignmentOffset
 *         };
 *     }
 *
 *     void commitRunBuffer(const RunInfo& info) override {
 *         fCurrentPosition += info.fAdvance;
 *     }
 *
 *     void commitLine() override {
 *         fOffset.fY += fDesc.fLineHeight;
 *
 *         // Observed AE handling of whitespace, for alignment purposes:
 *         //
 *         //   - leading whitespace contributes to alignment
 *         //   - trailing whitespace is ignored
 *         //   - auto line breaking retains all separating whitespace on the first line (no artificial
 *         //     leading WS is created).
 *         auto adjust_trailing_whitespace = [this]() {
 *             // For left-alignment, trailing WS doesn't make any difference.
 *             if (fLineRuns.empty() || fDesc.fHAlign == SkTextUtils::Align::kLeft_Align) {
 *                 return;
 *             }
 *
 *             // Technically, trailing whitespace could span multiple runs, but realistically,
 *             // SkShaper has no reason to split it.  Hence we're only checking the last run.
 *             size_t ws_count = 0;
 *             for (size_t i = 0; i < fLineRuns.back().fSize; ++i) {
 *                 if (is_whitespace(fUTF8[fLineClusters[SkToInt(fLineGlyphCount - i - 1)]])) {
 *                     ++ws_count;
 *                 } else {
 *                     break;
 *                 }
 *             }
 *
 *             // No trailing whitespace.
 *             if (!ws_count) {
 *                 return;
 *             }
 *
 *             // Compute the cumulative whitespace advance.
 *             fAdvanceBuffer.resize(ws_count);
 *             fLineRuns.back().fFont.getWidths(
 *                      {fLineGlyphs.data() + fLineGlyphCount - ws_count, ws_count},
 *                      {fAdvanceBuffer.data(), ws_count});
 *
 *             const auto ws_advance = std::accumulate(fAdvanceBuffer.begin(),
 *                                                     fAdvanceBuffer.end(),
 *                                                     0.0f);
 *
 *             // Offset needed to compensate for whitespace.
 *             const auto offset = ws_advance*-fHAlignFactor;
 *
 *             // Shift the whole line horizontally by the computed offset.
 *             std::transform(fLinePos.data(),
 *                            fLinePos.data() + fLineGlyphCount,
 *                            fLinePos.data(),
 *                            [&offset](SkPoint pos) { return SkPoint{pos.fX + offset, pos.fY}; });
 *         };
 *
 *         adjust_trailing_whitespace();
 *
 *         const auto commit_proc = (fDesc.fFlags & Shaper::Flags::kFragmentGlyphs)
 *             ? &ResultBuilder::commitFragementedRun
 *             : &ResultBuilder::commitConsolidatedRun;
 *
 *         size_t run_offset = 0;
 *         for (const auto& rec : fLineRuns) {
 *             SkASSERT(run_offset < fLineGlyphCount);
 *             (this->*commit_proc)(rec,
 *                         fLineGlyphs.get()   + run_offset,
 *                         fLinePos.get()      + run_offset,
 *                         fLineClusters.get() + run_offset,
 *                         fLineCount);
 *             run_offset += rec.fSize;
 *         }
 *
 *         fLineCount++;
 *     }
 *
 *     Shaper::Result finalize(SkSize* shaped_size) {
 *         if (!(fDesc.fFlags & Shaper::Flags::kFragmentGlyphs)) {
 *             // All glyphs (if any) are pending in a single fragment.
 *             SkASSERT(fResult.fFragments.size() <= 1);
 *         }
 *
 *         const auto ascent = this->ascent();
 *
 *         // For visual VAlign modes, we use a hybrid extent box computed as the union of
 *         // actual visual bounds and the vertical typographical extent.
 *         //
 *         // This ensures that
 *         //
 *         //   a) text doesn't visually overflow the alignment boundaries
 *         //
 *         //   b) leading/trailing empty lines are still taken into account for alignment purposes
 *
 *         auto extent_box = [&](bool include_typographical_extent) {
 *             auto box = fResult.computeVisualBounds();
 *
 *             if (include_typographical_extent) {
 *                 // Hybrid visual alignment mode, based on typographical extent.
 *
 *                 // By default, first line is vertically-aligned on a baseline of 0.
 *                 // The typographical height considered for vertical alignment is the distance
 *                 // between the first line top (ascent) to the last line bottom (descent).
 *                 const auto typographical_top    = fBox.fTop + ascent,
 *                            typographical_bottom = fBox.fTop + fLastLineDescent +
 *                                           fDesc.fLineHeight*(fLineCount > 0 ? fLineCount - 1 : 0ul);
 *
 *                 box.fTop    = std::min(box.fTop,    typographical_top);
 *                 box.fBottom = std::max(box.fBottom, typographical_bottom);
 *             }
 *
 *             return box;
 *         };
 *
 *         // Only compute the extent box when needed.
 *         std::optional<SkRect> ebox;
 *
 *         // Vertical adjustments.
 *         float v_offset = -fDesc.fLineShift;
 *
 *         switch (fDesc.fVAlign) {
 *         case Shaper::VAlign::kTop:
 *             v_offset -= ascent;
 *             break;
 *         case Shaper::VAlign::kTopBaseline:
 *             // Default behavior.
 *             break;
 *         case Shaper::VAlign::kHybridTop:
 *         case Shaper::VAlign::kVisualTop:
 *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridTop));
 *             v_offset += fBox.fTop - ebox->fTop;
 *             break;
 *         case Shaper::VAlign::kHybridCenter:
 *         case Shaper::VAlign::kVisualCenter:
 *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridCenter));
 *             v_offset += fBox.centerY() - ebox->centerY();
 *             break;
 *         case Shaper::VAlign::kHybridBottom:
 *         case Shaper::VAlign::kVisualBottom:
 *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridBottom));
 *             v_offset += fBox.fBottom - ebox->fBottom;
 *             break;
 *         }
 *
 *         if (shaped_size) {
 *             if (!ebox.has_value()) {
 *                 ebox.emplace(extent_box(true));
 *             }
 *             *shaped_size = SkSize::Make(ebox->width(), ebox->height());
 *         }
 *
 *         if (v_offset) {
 *             for (auto& fragment : fResult.fFragments) {
 *                 fragment.fOrigin.fY += v_offset;
 *             }
 *         }
 *
 *         return std::move(fResult);
 *     }
 *
 *     void shapeLine(const char* start, const char* end, size_t utf8_offset) {
 *         if (!fShaper) {
 *             return;
 *         }
 *
 *         SkASSERT(start <= end);
 *         if (start == end) {
 *             // SkShaper doesn't care for empty lines.
 *             this->beginLine();
 *             this->commitLine();
 *
 *             // The calls above perform bookkeeping, but they do not add any fragments (since there
 *             // are no runs to commit).
 *             //
 *             // Certain Skottie features (line-based range selectors) do require accurate indexing
 *             // information even for empty lines though -- so we inject empty fragments solely for
 *             // line index tracking.
 *             //
 *             // Note: we don't add empty fragments in consolidated mode because 1) consolidated mode
 *             // assumes there is a single result fragment and 2) kFragmentGlyphs is always enabled
 *             // for cases where line index tracking is relevant.
 *             //
 *             // TODO(fmalita): investigate whether it makes sense to move this special case down
 *             // to commitFragmentedRun().
 *             if (fDesc.fFlags & Shaper::Flags::kFragmentGlyphs) {
 *                 fResult.fFragments.push_back({
 *                     Shaper::ShapedGlyphs(),
 *                     {fBox.x(),fBox.y()},
 *                     0, 0,
 *                     fLineCount - 1,
 *                     false
 *                 });
 *             }
 *
 *             return;
 *         }
 *
 *         // In default paragraph mode (VAlign::kTop), AE clips out lines when the baseline
 *         // goes below the box lower edge.
 *         if (fDesc.fVAlign == Shaper::VAlign::kTop) {
 *             // fOffset is relative to the first line baseline.
 *             const auto max_offset = fBox.height() + this->ascent(); // NB: ascent is negative
 *             if (fOffset.y() > max_offset) {
 *                 return;
 *             }
 *         }
 *
 *         const auto shape_width  = fDesc.fLinebreak == Shaper::LinebreakPolicy::kExplicit
 *                                     ? SK_ScalarMax
 *                                     : fBox.width();
 *         const auto shape_ltr    = fDesc.fDirection == Shaper::Direction::kLTR;
 *         const size_t utf8_bytes = SkToSizeT(end - start);
 *
 *         static constexpr uint8_t kBidiLevelLTR = 0,
 *                                  kBidiLevelRTL = 1;
 *         const auto lang_iter = fDesc.fLocale
 *                 ? std::make_unique<SkShaper::TrivialLanguageRunIterator>(fDesc.fLocale, utf8_bytes)
 *                 : SkShaper::MakeStdLanguageRunIterator(start, utf8_bytes);
 * #if defined(SKOTTIE_TRIVIAL_FONTRUN_ITER)
 *         // Chrome Linux/CrOS does not have a fallback-capable fontmgr, and crashes if fallback is
 *         // triggered.  Using a TrivialFontRunIterator avoids the issue (https://crbug.com/1520148).
 *         const auto font_iter = std::make_unique<SkShaper::TrivialFontRunIterator>(fFont,
 *                                                                                   utf8_bytes);
 * #else
 *         const auto font_iter = SkShaper::MakeFontMgrRunIterator(
 *                                     start, utf8_bytes, fFont,
 *                                     fFontMgr ? fFontMgr : SkFontMgr::RefEmpty(), // used as fallback
 *                                     fDesc.fFontFamily,
 *                                     fFont.getTypeface()->fontStyle(),
 *                                     lang_iter.get());
 * #endif
 *
 *         std::unique_ptr<SkShaper::BiDiRunIterator> bidi_iter =
 *                 fShapingFactory->makeBidiRunIterator(start, utf8_bytes,
 *                                                   shape_ltr ? kBidiLevelLTR : kBidiLevelRTL);
 *         if (!bidi_iter) {
 *             bidi_iter = std::make_unique<SkShaper::TrivialBiDiRunIterator>(
 *                     shape_ltr ? kBidiLevelLTR : kBidiLevelRTL, utf8_bytes);
 *         }
 *
 *         constexpr SkFourByteTag unknownScript = SkSetFourByteTag('Z', 'z', 'z', 'z');
 *         std::unique_ptr<SkShaper::ScriptRunIterator> scpt_iter =
 *                 fShapingFactory->makeScriptRunIterator(start, utf8_bytes, unknownScript);
 *         if (!scpt_iter) {
 *             scpt_iter = std::make_unique<SkShaper::TrivialScriptRunIterator>(unknownScript, utf8_bytes);
 *         }
 *
 *         if (!font_iter || !bidi_iter || !scpt_iter || !lang_iter) {
 *             return;
 *         }
 *
 *         fUTF8 = start;
 *         fUTF8Offset = utf8_offset;
 *         fShaper->shape(start,
 *                        utf8_bytes,
 *                        *font_iter,
 *                        *bidi_iter,
 *                        *scpt_iter,
 *                        *lang_iter,
 *                        nullptr,
 *                        0,
 *                        shape_width,
 *                        this);
 *         fUTF8 = nullptr;
 *     }
 *
 * private:
 *     void commitFragementedRun(const skottie::Shaper::RunRec& run,
 *                               const SkGlyphID* glyphs,
 *                               const SkPoint* pos,
 *                               const uint32_t* clusters,
 *                               uint32_t line_index) {
 *         float ascent = 0;
 *
 *         if (fDesc.fFlags & Shaper::Flags::kTrackFragmentAdvanceAscent) {
 *             SkFontMetrics metrics;
 *             run.fFont.getMetrics(&metrics);
 *             ascent = metrics.fAscent;
 *
 *             // Note: we use per-glyph advances for anchoring, but it's unclear whether this
 *             // is exactly the same as AE.  E.g. are 'acute' glyphs anchored separately for fonts
 *             // in which they're distinct?
 *             fAdvanceBuffer.resize(run.fSize);
 *             fFont.getWidths({glyphs, run.fSize}, {fAdvanceBuffer.data(), run.fSize});
 *         }
 *
 *         // In fragmented mode we immediately push the glyphs to fResult,
 *         // one fragment per glyph.  Glyph positioning is externalized
 *         // (positions returned in Fragment::fPos).
 *         for (size_t i = 0; i < run.fSize; ++i) {
 *             const auto advance = (fDesc.fFlags & Shaper::Flags::kTrackFragmentAdvanceAscent)
 *                     ? fAdvanceBuffer[SkToInt(i)]
 *                     : 0.0f;
 *
 *             fResult.fFragments.push_back({
 *                 {
 *                     { {run.fFont, 1} },
 *                     { glyphs[i] },
 *                     { {0,0} },
 *                     fDesc.fFlags & Shaper::kClusters
 *                         ? std::vector<size_t>{ fUTF8Offset + clusters[i] }
 *                         : std::vector<size_t>({}),
 *                 },
 *                 { fBox.x() + pos[i].fX, fBox.y() + pos[i].fY },
 *                 advance, ascent,
 *                 line_index, is_whitespace(fUTF8[clusters[i]])
 *             });
 *
 *             // Note: we only check the first code point in the cluster for whitespace.
 *             // It's unclear whether thers's a saner approach.
 *             fResult.fMissingGlyphCount += (glyphs[i] == kMissingGlyphID);
 *         }
 *     }
 *
 *     void commitConsolidatedRun(const skottie::Shaper::RunRec& run,
 *                                const SkGlyphID* glyphs,
 *                                const SkPoint* pos,
 *                                const uint32_t* clusters,
 *                                uint32_t) {
 *         // In consolidated mode we just accumulate glyphs to a single fragment in ResultBuilder.
 *         // Glyph positions are baked in the fragment runs (Fragment::fPos only reflects the
 *         // box origin).
 *
 *         if (fResult.fFragments.empty()) {
 *             fResult.fFragments.push_back({{{}, {}, {}, {}}, {fBox.x(), fBox.y()}, 0, 0, 0, false});
 *         }
 *
 *         auto& current_glyphs = fResult.fFragments.back().fGlyphs;
 *         current_glyphs.fRuns.push_back(run);
 *         current_glyphs.fGlyphIDs.insert(current_glyphs.fGlyphIDs.end(), glyphs, glyphs + run.fSize);
 *         current_glyphs.fGlyphPos.insert(current_glyphs.fGlyphPos.end(), pos   , pos    + run.fSize);
 *
 *         for (size_t i = 0; i < run.fSize; ++i) {
 *             fResult.fMissingGlyphCount += (glyphs[i] == kMissingGlyphID);
 *         }
 *
 *         if (fDesc.fFlags & Shaper::kClusters) {
 *             current_glyphs.fClusters.reserve(current_glyphs.fClusters.size() + run.fSize);
 *             for (size_t i = 0; i < run.fSize; ++i) {
 *                 current_glyphs.fClusters.push_back(fUTF8Offset + clusters[i]);
 *             }
 *         }
 *     }
 *
 *     static float HAlignFactor(SkTextUtils::Align align) {
 *         switch (align) {
 *         case SkTextUtils::kLeft_Align:   return  0.0f;
 *         case SkTextUtils::kCenter_Align: return -0.5f;
 *         case SkTextUtils::kRight_Align:  return -1.0f;
 *         }
 *         return 0.0f; // go home, msvc...
 *     }
 *
 *     SkScalar ascent() const {
 *         // Use the explicit ascent, when specified.
 *         // Note: ascent values are negative (relative to the baseline).
 *         return fDesc.fAscent ? fDesc.fAscent : fFirstLineAscent;
 *     }
 *
 *     inline static constexpr SkGlyphID kMissingGlyphID = 0;
 *
 *     const Shaper::TextDesc&   fDesc;
 *     const SkRect&             fBox;
 *     const float               fHAlignFactor;
 *
 *     SkFont                          fFont;
 *     const sk_sp<SkFontMgr>          fFontMgr;
 *     std::unique_ptr<SkShaper>       fShaper;
 *     sk_sp<SkShapers::Factory>       fShapingFactory;
 *
 *     AutoSTMalloc<64, SkGlyphID>          fLineGlyphs;
 *     AutoSTMalloc<64, SkPoint>            fLinePos;
 *     AutoSTMalloc<64, uint32_t>           fLineClusters;
 *     STArray<16, skottie::Shaper::RunRec> fLineRuns;
 *     size_t                                 fLineGlyphCount = 0;
 *
 *     STArray<64, float, true> fAdvanceBuffer;
 *
 *     SkPoint  fCurrentPosition{ 0, 0 };
 *     SkPoint  fOffset{ 0, 0 };
 *     SkVector fPendingLineAdvance{ 0, 0 };
 *     uint32_t fLineCount = 0;
 *     float    fFirstLineAscent = 0,
 *              fLastLineDescent = 0;
 *
 *     const char* fUTF8       = nullptr; // only valid during shapeLine() calls
 *     size_t      fUTF8Offset = 0;       // current line offset within the original string
 *
 *     Shaper::Result fResult;
 * }
 * ```
 */
public class ResultBuilder public constructor(
  desc: Shaper.TextDesc,
  box: SkRect,
  fontmgr: SkSp<SkFontMgr>,
  shapingFactory: SkSp<Factory>,
) : SkShaper.RunHandler() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkGlyphID kMissingGlyphID = 0
   * ```
   */
  private val fDesc: Shaper.TextDesc = TODO("Initialize fDesc")

  /**
   * C++ original:
   * ```cpp
   * const Shaper::TextDesc&   fDesc
   * ```
   */
  private val fBox: SkRect = TODO("Initialize fBox")

  /**
   * C++ original:
   * ```cpp
   * const SkRect&             fBox
   * ```
   */
  private val fHAlignFactor: Float = TODO("Initialize fHAlignFactor")

  /**
   * C++ original:
   * ```cpp
   * const float               fHAlignFactor
   * ```
   */
  private var fFont: SkFont = TODO("Initialize fFont")

  /**
   * C++ original:
   * ```cpp
   * SkFont                          fFont
   * ```
   */
  private val fFontMgr: SkSp<SkFontMgr> = TODO("Initialize fFontMgr")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkFontMgr>          fFontMgr
   * ```
   */
  private var fShaper: Int = TODO("Initialize fShaper")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper>       fShaper
   * ```
   */
  private var fShapingFactory: SkSp<Factory> = TODO("Initialize fShapingFactory")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShapers::Factory>       fShapingFactory
   * ```
   */
  private var fLineGlyphs: Int = TODO("Initialize fLineGlyphs")

  /**
   * C++ original:
   * ```cpp
   * AutoSTMalloc<64, SkGlyphID>          fLineGlyphs
   * ```
   */
  private var fLinePos: Int = TODO("Initialize fLinePos")

  /**
   * C++ original:
   * ```cpp
   * AutoSTMalloc<64, SkPoint>            fLinePos
   * ```
   */
  private var fLineClusters: Int = TODO("Initialize fLineClusters")

  /**
   * C++ original:
   * ```cpp
   * AutoSTMalloc<64, uint32_t>           fLineClusters
   * ```
   */
  private var fLineRuns: Int = TODO("Initialize fLineRuns")

  /**
   * C++ original:
   * ```cpp
   * STArray<16, skottie::Shaper::RunRec> fLineRuns
   * ```
   */
  private var fLineGlyphCount: Int = TODO("Initialize fLineGlyphCount")

  /**
   * C++ original:
   * ```cpp
   * size_t                                 fLineGlyphCount
   * ```
   */
  private var fAdvanceBuffer: STArray64True<Float> = TODO("Initialize fAdvanceBuffer")

  /**
   * C++ original:
   * ```cpp
   * STArray<64, float, true> fAdvanceBuffer
   * ```
   */
  private var fCurrentPosition: SkPoint = TODO("Initialize fCurrentPosition")

  /**
   * C++ original:
   * ```cpp
   * SkPoint  fCurrentPosition{ 0, 0 }
   * ```
   */
  private var fOffset: SkPoint = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * SkPoint  fOffset{ 0, 0 }
   * ```
   */
  private var fPendingLineAdvance: SkVector = TODO("Initialize fPendingLineAdvance")

  /**
   * C++ original:
   * ```cpp
   * SkVector fPendingLineAdvance{ 0, 0 }
   * ```
   */
  private var fLineCount: Int = TODO("Initialize fLineCount")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fLineCount
   * ```
   */
  private var fFirstLineAscent: Float = TODO("Initialize fFirstLineAscent")

  /**
   * C++ original:
   * ```cpp
   * float    fFirstLineAscent = 0
   * ```
   */
  private var fLastLineDescent: Float = TODO("Initialize fLastLineDescent")

  /**
   * C++ original:
   * ```cpp
   * float    fFirstLineAscent = 0,
   *              fLastLineDescent = 0
   * ```
   */
  private val fUTF8: String? = TODO("Initialize fUTF8")

  /**
   * C++ original:
   * ```cpp
   * const char* fUTF8       = nullptr
   * ```
   */
  private var fUTF8Offset: Int = TODO("Initialize fUTF8Offset")

  /**
   * C++ original:
   * ```cpp
   * size_t      fUTF8Offset
   * ```
   */
  private var fResult: Shaper.Result = TODO("Initialize fResult")

  /**
   * C++ original:
   * ```cpp
   * void beginLine() override {
   *         fLineGlyphs.reset(0);
   *         fLinePos.reset(0);
   *         fLineClusters.reset(0);
   *         fLineRuns.clear();
   *         fLineGlyphCount = 0;
   *
   *         fCurrentPosition = fOffset;
   *         fPendingLineAdvance  = { 0, 0 };
   *
   *         fLastLineDescent = 0;
   *     }
   * ```
   */
  public override fun beginLine() {
    TODO("Implement beginLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void runInfo(const RunInfo& info) override {
   *         fPendingLineAdvance += info.fAdvance;
   *
   *         SkFontMetrics metrics;
   *         info.fFont.getMetrics(&metrics);
   *         if (!fLineCount) {
   *             fFirstLineAscent = std::min(fFirstLineAscent, metrics.fAscent);
   *         }
   *         fLastLineDescent = std::max(fLastLineDescent, metrics.fDescent);
   *     }
   * ```
   */
  public override fun runInfo(info: RunInfo) {
    TODO("Implement runInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitRunInfo() override {}
   * ```
   */
  public override fun commitRunInfo() {
    TODO("Implement commitRunInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * Buffer runBuffer(const RunInfo& info) override {
   *         const auto run_start_index = fLineGlyphCount;
   *         fLineGlyphCount += info.glyphCount;
   *
   *         fLineGlyphs.realloc(fLineGlyphCount);
   *         fLinePos.realloc(fLineGlyphCount);
   *         fLineClusters.realloc(fLineGlyphCount);
   *         fLineRuns.push_back({info.fFont, info.glyphCount});
   *
   *         SkVector alignmentOffset { fHAlignFactor * (fPendingLineAdvance.x() - fBox.width()), 0 };
   *
   *         return {
   *             fLineGlyphs.get()   + run_start_index,
   *             fLinePos.get()      + run_start_index,
   *             nullptr,
   *             fLineClusters.get() + run_start_index,
   *             fCurrentPosition + alignmentOffset
   *         };
   *     }
   * ```
   */
  public override fun runBuffer(info: RunInfo): Buffer {
    TODO("Implement runBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitRunBuffer(const RunInfo& info) override {
   *         fCurrentPosition += info.fAdvance;
   *     }
   * ```
   */
  public override fun commitRunBuffer(info: RunInfo) {
    TODO("Implement commitRunBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitLine() override {
   *         fOffset.fY += fDesc.fLineHeight;
   *
   *         // Observed AE handling of whitespace, for alignment purposes:
   *         //
   *         //   - leading whitespace contributes to alignment
   *         //   - trailing whitespace is ignored
   *         //   - auto line breaking retains all separating whitespace on the first line (no artificial
   *         //     leading WS is created).
   *         auto adjust_trailing_whitespace = [this]() {
   *             // For left-alignment, trailing WS doesn't make any difference.
   *             if (fLineRuns.empty() || fDesc.fHAlign == SkTextUtils::Align::kLeft_Align) {
   *                 return;
   *             }
   *
   *             // Technically, trailing whitespace could span multiple runs, but realistically,
   *             // SkShaper has no reason to split it.  Hence we're only checking the last run.
   *             size_t ws_count = 0;
   *             for (size_t i = 0; i < fLineRuns.back().fSize; ++i) {
   *                 if (is_whitespace(fUTF8[fLineClusters[SkToInt(fLineGlyphCount - i - 1)]])) {
   *                     ++ws_count;
   *                 } else {
   *                     break;
   *                 }
   *             }
   *
   *             // No trailing whitespace.
   *             if (!ws_count) {
   *                 return;
   *             }
   *
   *             // Compute the cumulative whitespace advance.
   *             fAdvanceBuffer.resize(ws_count);
   *             fLineRuns.back().fFont.getWidths(
   *                      {fLineGlyphs.data() + fLineGlyphCount - ws_count, ws_count},
   *                      {fAdvanceBuffer.data(), ws_count});
   *
   *             const auto ws_advance = std::accumulate(fAdvanceBuffer.begin(),
   *                                                     fAdvanceBuffer.end(),
   *                                                     0.0f);
   *
   *             // Offset needed to compensate for whitespace.
   *             const auto offset = ws_advance*-fHAlignFactor;
   *
   *             // Shift the whole line horizontally by the computed offset.
   *             std::transform(fLinePos.data(),
   *                            fLinePos.data() + fLineGlyphCount,
   *                            fLinePos.data(),
   *                            [&offset](SkPoint pos) { return SkPoint{pos.fX + offset, pos.fY}; });
   *         };
   *
   *         adjust_trailing_whitespace();
   *
   *         const auto commit_proc = (fDesc.fFlags & Shaper::Flags::kFragmentGlyphs)
   *             ? &ResultBuilder::commitFragementedRun
   *             : &ResultBuilder::commitConsolidatedRun;
   *
   *         size_t run_offset = 0;
   *         for (const auto& rec : fLineRuns) {
   *             SkASSERT(run_offset < fLineGlyphCount);
   *             (this->*commit_proc)(rec,
   *                         fLineGlyphs.get()   + run_offset,
   *                         fLinePos.get()      + run_offset,
   *                         fLineClusters.get() + run_offset,
   *                         fLineCount);
   *             run_offset += rec.fSize;
   *         }
   *
   *         fLineCount++;
   *     }
   * ```
   */
  public override fun commitLine() {
    TODO("Implement commitLine")
  }

  /**
   * C++ original:
   * ```cpp
   * Shaper::Result finalize(SkSize* shaped_size) {
   *         if (!(fDesc.fFlags & Shaper::Flags::kFragmentGlyphs)) {
   *             // All glyphs (if any) are pending in a single fragment.
   *             SkASSERT(fResult.fFragments.size() <= 1);
   *         }
   *
   *         const auto ascent = this->ascent();
   *
   *         // For visual VAlign modes, we use a hybrid extent box computed as the union of
   *         // actual visual bounds and the vertical typographical extent.
   *         //
   *         // This ensures that
   *         //
   *         //   a) text doesn't visually overflow the alignment boundaries
   *         //
   *         //   b) leading/trailing empty lines are still taken into account for alignment purposes
   *
   *         auto extent_box = [&](bool include_typographical_extent) {
   *             auto box = fResult.computeVisualBounds();
   *
   *             if (include_typographical_extent) {
   *                 // Hybrid visual alignment mode, based on typographical extent.
   *
   *                 // By default, first line is vertically-aligned on a baseline of 0.
   *                 // The typographical height considered for vertical alignment is the distance
   *                 // between the first line top (ascent) to the last line bottom (descent).
   *                 const auto typographical_top    = fBox.fTop + ascent,
   *                            typographical_bottom = fBox.fTop + fLastLineDescent +
   *                                           fDesc.fLineHeight*(fLineCount > 0 ? fLineCount - 1 : 0ul);
   *
   *                 box.fTop    = std::min(box.fTop,    typographical_top);
   *                 box.fBottom = std::max(box.fBottom, typographical_bottom);
   *             }
   *
   *             return box;
   *         };
   *
   *         // Only compute the extent box when needed.
   *         std::optional<SkRect> ebox;
   *
   *         // Vertical adjustments.
   *         float v_offset = -fDesc.fLineShift;
   *
   *         switch (fDesc.fVAlign) {
   *         case Shaper::VAlign::kTop:
   *             v_offset -= ascent;
   *             break;
   *         case Shaper::VAlign::kTopBaseline:
   *             // Default behavior.
   *             break;
   *         case Shaper::VAlign::kHybridTop:
   *         case Shaper::VAlign::kVisualTop:
   *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridTop));
   *             v_offset += fBox.fTop - ebox->fTop;
   *             break;
   *         case Shaper::VAlign::kHybridCenter:
   *         case Shaper::VAlign::kVisualCenter:
   *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridCenter));
   *             v_offset += fBox.centerY() - ebox->centerY();
   *             break;
   *         case Shaper::VAlign::kHybridBottom:
   *         case Shaper::VAlign::kVisualBottom:
   *             ebox.emplace(extent_box(fDesc.fVAlign == Shaper::VAlign::kHybridBottom));
   *             v_offset += fBox.fBottom - ebox->fBottom;
   *             break;
   *         }
   *
   *         if (shaped_size) {
   *             if (!ebox.has_value()) {
   *                 ebox.emplace(extent_box(true));
   *             }
   *             *shaped_size = SkSize::Make(ebox->width(), ebox->height());
   *         }
   *
   *         if (v_offset) {
   *             for (auto& fragment : fResult.fFragments) {
   *                 fragment.fOrigin.fY += v_offset;
   *             }
   *         }
   *
   *         return std::move(fResult);
   *     }
   * ```
   */
  public fun finalize(shapedSize: SkSize?): Shaper.Result {
    TODO("Implement finalize")
  }

  /**
   * C++ original:
   * ```cpp
   * void shapeLine(const char* start, const char* end, size_t utf8_offset) {
   *         if (!fShaper) {
   *             return;
   *         }
   *
   *         SkASSERT(start <= end);
   *         if (start == end) {
   *             // SkShaper doesn't care for empty lines.
   *             this->beginLine();
   *             this->commitLine();
   *
   *             // The calls above perform bookkeeping, but they do not add any fragments (since there
   *             // are no runs to commit).
   *             //
   *             // Certain Skottie features (line-based range selectors) do require accurate indexing
   *             // information even for empty lines though -- so we inject empty fragments solely for
   *             // line index tracking.
   *             //
   *             // Note: we don't add empty fragments in consolidated mode because 1) consolidated mode
   *             // assumes there is a single result fragment and 2) kFragmentGlyphs is always enabled
   *             // for cases where line index tracking is relevant.
   *             //
   *             // TODO(fmalita): investigate whether it makes sense to move this special case down
   *             // to commitFragmentedRun().
   *             if (fDesc.fFlags & Shaper::Flags::kFragmentGlyphs) {
   *                 fResult.fFragments.push_back({
   *                     Shaper::ShapedGlyphs(),
   *                     {fBox.x(),fBox.y()},
   *                     0, 0,
   *                     fLineCount - 1,
   *                     false
   *                 });
   *             }
   *
   *             return;
   *         }
   *
   *         // In default paragraph mode (VAlign::kTop), AE clips out lines when the baseline
   *         // goes below the box lower edge.
   *         if (fDesc.fVAlign == Shaper::VAlign::kTop) {
   *             // fOffset is relative to the first line baseline.
   *             const auto max_offset = fBox.height() + this->ascent(); // NB: ascent is negative
   *             if (fOffset.y() > max_offset) {
   *                 return;
   *             }
   *         }
   *
   *         const auto shape_width  = fDesc.fLinebreak == Shaper::LinebreakPolicy::kExplicit
   *                                     ? SK_ScalarMax
   *                                     : fBox.width();
   *         const auto shape_ltr    = fDesc.fDirection == Shaper::Direction::kLTR;
   *         const size_t utf8_bytes = SkToSizeT(end - start);
   *
   *         static constexpr uint8_t kBidiLevelLTR = 0,
   *                                  kBidiLevelRTL = 1;
   *         const auto lang_iter = fDesc.fLocale
   *                 ? std::make_unique<SkShaper::TrivialLanguageRunIterator>(fDesc.fLocale, utf8_bytes)
   *                 : SkShaper::MakeStdLanguageRunIterator(start, utf8_bytes);
   * #if defined(SKOTTIE_TRIVIAL_FONTRUN_ITER)
   *         // Chrome Linux/CrOS does not have a fallback-capable fontmgr, and crashes if fallback is
   *         // triggered.  Using a TrivialFontRunIterator avoids the issue (https://crbug.com/1520148).
   *         const auto font_iter = std::make_unique<SkShaper::TrivialFontRunIterator>(fFont,
   *                                                                                   utf8_bytes);
   * #else
   *         const auto font_iter = SkShaper::MakeFontMgrRunIterator(
   *                                     start, utf8_bytes, fFont,
   *                                     fFontMgr ? fFontMgr : SkFontMgr::RefEmpty(), // used as fallback
   *                                     fDesc.fFontFamily,
   *                                     fFont.getTypeface()->fontStyle(),
   *                                     lang_iter.get());
   * #endif
   *
   *         std::unique_ptr<SkShaper::BiDiRunIterator> bidi_iter =
   *                 fShapingFactory->makeBidiRunIterator(start, utf8_bytes,
   *                                                   shape_ltr ? kBidiLevelLTR : kBidiLevelRTL);
   *         if (!bidi_iter) {
   *             bidi_iter = std::make_unique<SkShaper::TrivialBiDiRunIterator>(
   *                     shape_ltr ? kBidiLevelLTR : kBidiLevelRTL, utf8_bytes);
   *         }
   *
   *         constexpr SkFourByteTag unknownScript = SkSetFourByteTag('Z', 'z', 'z', 'z');
   *         std::unique_ptr<SkShaper::ScriptRunIterator> scpt_iter =
   *                 fShapingFactory->makeScriptRunIterator(start, utf8_bytes, unknownScript);
   *         if (!scpt_iter) {
   *             scpt_iter = std::make_unique<SkShaper::TrivialScriptRunIterator>(unknownScript, utf8_bytes);
   *         }
   *
   *         if (!font_iter || !bidi_iter || !scpt_iter || !lang_iter) {
   *             return;
   *         }
   *
   *         fUTF8 = start;
   *         fUTF8Offset = utf8_offset;
   *         fShaper->shape(start,
   *                        utf8_bytes,
   *                        *font_iter,
   *                        *bidi_iter,
   *                        *scpt_iter,
   *                        *lang_iter,
   *                        nullptr,
   *                        0,
   *                        shape_width,
   *                        this);
   *         fUTF8 = nullptr;
   *     }
   * ```
   */
  public fun shapeLine(
    start: String?,
    end: String?,
    utf8Offset: ULong,
  ) {
    TODO("Implement shapeLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitFragementedRun(const skottie::Shaper::RunRec& run,
   *                               const SkGlyphID* glyphs,
   *                               const SkPoint* pos,
   *                               const uint32_t* clusters,
   *                               uint32_t line_index) {
   *         float ascent = 0;
   *
   *         if (fDesc.fFlags & Shaper::Flags::kTrackFragmentAdvanceAscent) {
   *             SkFontMetrics metrics;
   *             run.fFont.getMetrics(&metrics);
   *             ascent = metrics.fAscent;
   *
   *             // Note: we use per-glyph advances for anchoring, but it's unclear whether this
   *             // is exactly the same as AE.  E.g. are 'acute' glyphs anchored separately for fonts
   *             // in which they're distinct?
   *             fAdvanceBuffer.resize(run.fSize);
   *             fFont.getWidths({glyphs, run.fSize}, {fAdvanceBuffer.data(), run.fSize});
   *         }
   *
   *         // In fragmented mode we immediately push the glyphs to fResult,
   *         // one fragment per glyph.  Glyph positioning is externalized
   *         // (positions returned in Fragment::fPos).
   *         for (size_t i = 0; i < run.fSize; ++i) {
   *             const auto advance = (fDesc.fFlags & Shaper::Flags::kTrackFragmentAdvanceAscent)
   *                     ? fAdvanceBuffer[SkToInt(i)]
   *                     : 0.0f;
   *
   *             fResult.fFragments.push_back({
   *                 {
   *                     { {run.fFont, 1} },
   *                     { glyphs[i] },
   *                     { {0,0} },
   *                     fDesc.fFlags & Shaper::kClusters
   *                         ? std::vector<size_t>{ fUTF8Offset + clusters[i] }
   *                         : std::vector<size_t>({}),
   *                 },
   *                 { fBox.x() + pos[i].fX, fBox.y() + pos[i].fY },
   *                 advance, ascent,
   *                 line_index, is_whitespace(fUTF8[clusters[i]])
   *             });
   *
   *             // Note: we only check the first code point in the cluster for whitespace.
   *             // It's unclear whether thers's a saner approach.
   *             fResult.fMissingGlyphCount += (glyphs[i] == kMissingGlyphID);
   *         }
   *     }
   * ```
   */
  private fun commitFragementedRun(
    run: Shaper.RunRec,
    glyphs: SkGlyphID?,
    pos: SkPoint?,
    clusters: UInt?,
    lineIndex: UInt,
  ) {
    TODO("Implement commitFragementedRun")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitConsolidatedRun(const skottie::Shaper::RunRec& run,
   *                                const SkGlyphID* glyphs,
   *                                const SkPoint* pos,
   *                                const uint32_t* clusters,
   *                                uint32_t) {
   *         // In consolidated mode we just accumulate glyphs to a single fragment in ResultBuilder.
   *         // Glyph positions are baked in the fragment runs (Fragment::fPos only reflects the
   *         // box origin).
   *
   *         if (fResult.fFragments.empty()) {
   *             fResult.fFragments.push_back({{{}, {}, {}, {}}, {fBox.x(), fBox.y()}, 0, 0, 0, false});
   *         }
   *
   *         auto& current_glyphs = fResult.fFragments.back().fGlyphs;
   *         current_glyphs.fRuns.push_back(run);
   *         current_glyphs.fGlyphIDs.insert(current_glyphs.fGlyphIDs.end(), glyphs, glyphs + run.fSize);
   *         current_glyphs.fGlyphPos.insert(current_glyphs.fGlyphPos.end(), pos   , pos    + run.fSize);
   *
   *         for (size_t i = 0; i < run.fSize; ++i) {
   *             fResult.fMissingGlyphCount += (glyphs[i] == kMissingGlyphID);
   *         }
   *
   *         if (fDesc.fFlags & Shaper::kClusters) {
   *             current_glyphs.fClusters.reserve(current_glyphs.fClusters.size() + run.fSize);
   *             for (size_t i = 0; i < run.fSize; ++i) {
   *                 current_glyphs.fClusters.push_back(fUTF8Offset + clusters[i]);
   *             }
   *         }
   *     }
   * ```
   */
  private fun commitConsolidatedRun(
    run: Shaper.RunRec,
    glyphs: SkGlyphID?,
    pos: SkPoint?,
    clusters: UInt?,
    param4: UInt,
  ) {
    TODO("Implement commitConsolidatedRun")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar ascent() const {
   *         // Use the explicit ascent, when specified.
   *         // Note: ascent values are negative (relative to the baseline).
   *         return fDesc.fAscent ? fDesc.fAscent : fFirstLineAscent;
   *     }
   * ```
   */
  private fun ascent(): SkScalar {
    TODO("Implement ascent")
  }

  public companion object {
    private val kMissingGlyphID: SkGlyphID = TODO("Initialize kMissingGlyphID")

    /**
     * C++ original:
     * ```cpp
     * static float HAlignFactor(SkTextUtils::Align align) {
     *         switch (align) {
     *         case SkTextUtils::kLeft_Align:   return  0.0f;
     *         case SkTextUtils::kCenter_Align: return -0.5f;
     *         case SkTextUtils::kRight_Align:  return -1.0f;
     *         }
     *         return 0.0f; // go home, msvc...
     *     }
     * ```
     */
    private fun hAlignFactor(align: SkTextUtils.Align): Float {
      TODO("Implement hAlignFactor")
    }
  }
}
