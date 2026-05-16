package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.collections.List
import org.skia.math.SkScalar
import undefined.ClustersVisitor
import undefined.RunStyleVisitor
import undefined.RunVisitor

/**
 * C++ original:
 * ```cpp
 * class TextLine {
 * public:
 *
 *     struct ClipContext {
 *       const Run* run;
 *       size_t pos;
 *       size_t size;
 *       SkScalar fTextShift; // Shifts the text inside the run so it's placed at the right position
 *       SkRect clip;
 *       SkScalar fExcludedTrailingSpaces;
 *       bool clippingNeeded;
 *     };
 *
 *     enum TextAdjustment {
 *         GlyphCluster = 0x01,    // All text producing glyphs pointing to the same ClusterIndex
 *         GlyphemeCluster = 0x02, // base glyph + all attached diacritics
 *         Grapheme = 0x04,        // Text adjusted to graphemes
 *         GraphemeGluster = 0x05, // GlyphCluster & Grapheme
 *     };
 *
 *     TextLine() = default;
 *     TextLine(const TextLine&) = delete;
 *     TextLine& operator=(const TextLine&) = delete;
 *     TextLine(TextLine&&) = default;
 *     TextLine& operator=(TextLine&&) = default;
 *     ~TextLine() = default;
 *
 *     TextLine(ParagraphImpl* owner,
 *              SkVector offset,
 *              SkVector advance,
 *              BlockRange blocks,
 *              TextRange textExcludingSpaces,
 *              TextRange text,
 *              TextRange textIncludingNewlines,
 *              ClusterRange clusters,
 *              ClusterRange clustersWithGhosts,
 *              SkScalar widthWithSpaces,
 *              InternalLineMetrics sizes);
 *
 *     TextRange trimmedText() const { return fTextExcludingSpaces; }
 *     TextRange textWithNewlines() const { return fTextIncludingNewlines; }
 *     TextRange text() const { return fText; }
 *     ClusterRange clusters() const { return fClusterRange; }
 *     ClusterRange clustersWithSpaces() const { return fGhostClusterRange; }
 *     Run* ellipsis() const { return fEllipsis.get(); }
 *     InternalLineMetrics sizes() const { return fSizes; }
 *     bool empty() const { return fTextExcludingSpaces.empty(); }
 *
 *     SkScalar spacesWidth() const { return fWidthWithSpaces - width(); }
 *     SkScalar height() const { return fAdvance.fY; }
 *     SkScalar width() const {
 *         return fAdvance.fX + (fEllipsis != nullptr ? fEllipsis->fAdvance.fX : 0);
 *     }
 *     SkScalar widthWithoutEllipsis() const { return fAdvance.fX; }
 *     SkVector offset() const;
 *
 *     SkScalar alphabeticBaseline() const { return fSizes.alphabeticBaseline(); }
 *     SkScalar ideographicBaseline() const { return fSizes.ideographicBaseline(); }
 *     SkScalar baseline() const { return fSizes.baseline(); }
 *
 *     using RunVisitor = std::function<bool(
 *             const Run* run, SkScalar runOffset, TextRange textRange, SkScalar* width)>;
 *     void iterateThroughVisualRuns(bool includingGhostSpaces, const RunVisitor& runVisitor) const;
 *     using RunStyleVisitor = std::function<void(
 *             TextRange textRange, const TextStyle& style, const ClipContext& context)>;
 *     SkScalar iterateThroughSingleRunByStyles(TextAdjustment textAdjustment,
 *                                              const Run* run,
 *                                              SkScalar runOffset,
 *                                              TextRange textRange,
 *                                              StyleType styleType,
 *                                              const RunStyleVisitor& visitor) const;
 *
 *     using ClustersVisitor = std::function<bool(const Cluster* cluster, ClusterIndex index, bool ghost)>;
 *     void iterateThroughClustersInGlyphsOrder(bool reverse,
 *                                              bool includeGhosts,
 *                                              const ClustersVisitor& visitor) const;
 *
 *     void format(TextAlign align, SkScalar maxWidth);
 *     void paint(ParagraphPainter* painter, SkScalar x, SkScalar y);
 *     void visit(SkScalar x, SkScalar y);
 *     void ensureTextBlobCachePopulated();
 *
 *     void createEllipsis(SkScalar maxWidth, const SkString& ellipsis, bool ltr);
 *
 *     // For testing internal structures
 *     void scanStyles(StyleType style, const RunStyleVisitor& visitor);
 *
 *     void setMaxRunMetrics(const InternalLineMetrics& metrics) { fMaxRunMetrics = metrics; }
 *     InternalLineMetrics getMaxRunMetrics() const { return fMaxRunMetrics; }
 *
 *     bool isFirstLine() const;
 *     bool isLastLine() const;
 *     void getRectsForRange(TextRange textRange,
 *                           RectHeightStyle rectHeightStyle,
 *                           RectWidthStyle rectWidthStyle,
 *                           std::vector<TextBox>& boxes) const;
 *     void getRectsForPlaceholders(std::vector<TextBox>& boxes);
 *     PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx);
 *
 *     ClipContext measureTextInsideOneRun(TextRange textRange,
 *                                         const Run* run,
 *                                         SkScalar runOffsetInLine,
 *                                         SkScalar textOffsetInRunInLine,
 *                                         bool includeGhostSpaces,
 *                                         TextAdjustment textAdjustment) const;
 *
 *     LineMetrics getMetrics() const;
 *
 *     SkRect extendHeight(const ClipContext& context) const;
 *
 *     void shiftVertically(SkScalar shift) { fOffset.fY += shift; }
 *
 *     void setAscentStyle(LineMetricStyle style) { fAscentStyle = style; }
 *     void setDescentStyle(LineMetricStyle style) { fDescentStyle = style; }
 *
 *     bool endsWithHardLineBreak() const;
 *
 * private:
 *     std::unique_ptr<Run> shapeEllipsis(const SkString& ellipsis, const Cluster* cluster);
 *     void justify(SkScalar maxWidth);
 *
 *     void buildTextBlob(TextRange textRange, const TextStyle& style, const ClipContext& context);
 *     void paintBackground(ParagraphPainter* painter,
 *                          SkScalar x,
 *                          SkScalar y,
 *                          TextRange textRange,
 *                          const TextStyle& style,
 *                          const ClipContext& context) const;
 *     void paintShadow(ParagraphPainter* painter,
 *                      SkScalar x,
 *                      SkScalar y,
 *                      TextRange textRange,
 *                      const TextStyle& style,
 *                      const ClipContext& context) const;
 *     void paintDecorations(ParagraphPainter* painter,
 *                           SkScalar x,
 *                           SkScalar y,
 *                           TextRange textRange,
 *                           const TextStyle& style,
 *                           const ClipContext& context) const;
 *
 *     void shiftCluster(const Cluster* cluster, SkScalar shift, SkScalar prevShift);
 *
 *     ParagraphImpl* fOwner;
 *     BlockRange fBlockRange;
 *     TextRange fTextExcludingSpaces;
 *     TextRange fText;
 *     TextRange fTextIncludingNewlines;
 *     ClusterRange fClusterRange;
 *     ClusterRange fGhostClusterRange;
 *     // Avoid the malloc/free in the common case of one run per line
 *     skia_private::STArray<1, size_t, true> fRunsInVisualOrder;
 *     SkVector fAdvance;                  // Text size
 *     SkVector fOffset;                   // Text position
 *     SkScalar fShift;                    // Let right
 *     SkScalar fWidthWithSpaces;
 *     std::unique_ptr<Run> fEllipsis;     // In case the line ends with the ellipsis
 *     InternalLineMetrics fSizes;                 // Line metrics as a max of all run metrics and struts
 *     InternalLineMetrics fMaxRunMetrics;         // No struts - need it for GetRectForRange(max height)
 *     bool fHasBackground;
 *     bool fHasShadows;
 *     bool fHasDecorations;
 *
 *     LineMetricStyle fAscentStyle;
 *     LineMetricStyle fDescentStyle;
 *
 *     struct TextBlobRecord {
 *         void paint(ParagraphPainter* painter, SkScalar x, SkScalar y);
 *
 *         sk_sp<SkTextBlob> fBlob;
 *         SkPoint fOffset = SkPoint::Make(0.0f, 0.0f);
 *         ParagraphPainter::SkPaintOrID fPaint;
 *         SkRect fBounds = SkRect::MakeEmpty();
 *         bool fClippingNeeded = false;
 *         SkRect fClipRect = SkRect::MakeEmpty();
 *
 *         // Extra fields only used for the (experimental) visitor
 *         const Run* fVisitor_Run;
 *         size_t     fVisitor_Pos;
 *     };
 *     bool fTextBlobCachePopulated;
 * public:
 *     std::vector<TextBlobRecord> fTextBlobCache;
 * }
 * ```
 */
public data class TextLine public constructor(
  /**
   * C++ original:
   * ```cpp
   * ParagraphImpl* fOwner
   * ```
   */
  private var fOwner: ParagraphImpl?,
  /**
   * C++ original:
   * ```cpp
   * BlockRange fBlockRange
   * ```
   */
  private var fBlockRange: Int,
  /**
   * C++ original:
   * ```cpp
   * TextRange fTextExcludingSpaces
   * ```
   */
  private var fTextExcludingSpaces: Int,
  /**
   * C++ original:
   * ```cpp
   * TextRange fText
   * ```
   */
  private var fText: Int,
  /**
   * C++ original:
   * ```cpp
   * TextRange fTextIncludingNewlines
   * ```
   */
  private var fTextIncludingNewlines: Int,
  /**
   * C++ original:
   * ```cpp
   * ClusterRange fClusterRange
   * ```
   */
  private var fClusterRange: Int,
  /**
   * C++ original:
   * ```cpp
   * ClusterRange fGhostClusterRange
   * ```
   */
  private var fGhostClusterRange: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<1, size_t, true> fRunsInVisualOrder
   * ```
   */
  private var fRunsInVisualOrder: Int,
  /**
   * C++ original:
   * ```cpp
   * SkVector fAdvance
   * ```
   */
  private var fAdvance: Int,
  /**
   * C++ original:
   * ```cpp
   * SkVector fOffset
   * ```
   */
  private var fOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fShift
   * ```
   */
  private var fShift: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWidthWithSpaces
   * ```
   */
  private var fWidthWithSpaces: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Run> fEllipsis
   * ```
   */
  private var fEllipsis: Int,
  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics fSizes
   * ```
   */
  private var fSizes: Int,
  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics fMaxRunMetrics
   * ```
   */
  private var fMaxRunMetrics: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHasBackground
   * ```
   */
  private var fHasBackground: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasShadows
   * ```
   */
  private var fHasShadows: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasDecorations
   * ```
   */
  private var fHasDecorations: Boolean,
  /**
   * C++ original:
   * ```cpp
   * LineMetricStyle fAscentStyle
   * ```
   */
  private var fAscentStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * LineMetricStyle fDescentStyle
   * ```
   */
  private var fDescentStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fTextBlobCachePopulated
   * ```
   */
  private var fTextBlobCachePopulated: Boolean,
  /**
   * C++ original:
   * ```cpp
   * std::vector<TextBlobRecord> fTextBlobCache
   * ```
   */
  public var fTextBlobCache: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TextLine& operator=(const TextLine&) = delete
   * ```
   */
  public fun assign(param0: TextLine) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * TextLine& operator=(TextLine&&) = default
   * ```
   */
  public fun trimmedText(): TextLine {
    TODO("Implement trimmedText")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange trimmedText() const { return fTextExcludingSpaces; }
   * ```
   */
  public fun textWithNewlines(): TextLine {
    TODO("Implement textWithNewlines")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange textWithNewlines() const { return fTextIncludingNewlines; }
   * ```
   */
  public fun text(): TextLine {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange text() const { return fText; }
   * ```
   */
  public fun clusters(): Int {
    TODO("Implement clusters")
  }

  /**
   * C++ original:
   * ```cpp
   * ClusterRange clusters() const { return fClusterRange; }
   * ```
   */
  public fun clustersWithSpaces(): Int {
    TODO("Implement clustersWithSpaces")
  }

  /**
   * C++ original:
   * ```cpp
   * ClusterRange clustersWithSpaces() const { return fGhostClusterRange; }
   * ```
   */
  public fun ellipsis(): Int {
    TODO("Implement ellipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * Run* ellipsis() const { return fEllipsis.get(); }
   * ```
   */
  public fun sizes(): Int {
    TODO("Implement sizes")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics sizes() const { return fSizes; }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fTextExcludingSpaces.empty(); }
   * ```
   */
  public fun spacesWidth(): Int {
    TODO("Implement spacesWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar spacesWidth() const { return fWidthWithSpaces - width(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() const { return fAdvance.fY; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar width() const {
   *         return fAdvance.fX + (fEllipsis != nullptr ? fEllipsis->fAdvance.fX : 0);
   *     }
   * ```
   */
  public fun widthWithoutEllipsis(): Int {
    TODO("Implement widthWithoutEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar widthWithoutEllipsis() const { return fAdvance.fX; }
   * ```
   */
  public fun offset(): Int {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector TextLine::offset() const {
   *     return fOffset + SkVector::Make(fShift, 0);
   * }
   * ```
   */
  public fun alphabeticBaseline(): Int {
    TODO("Implement alphabeticBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar alphabeticBaseline() const { return fSizes.alphabeticBaseline(); }
   * ```
   */
  public fun ideographicBaseline(): Int {
    TODO("Implement ideographicBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar ideographicBaseline() const { return fSizes.ideographicBaseline(); }
   * ```
   */
  public fun baseline(): Int {
    TODO("Implement baseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar baseline() const { return fSizes.baseline(); }
   * ```
   */
  public fun iterateThroughVisualRuns(includingGhostSpaces: Boolean, runVisitor: RunVisitor) {
    TODO("Implement iterateThroughVisualRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::iterateThroughVisualRuns(bool includingGhostSpaces, const RunVisitor& visitor) const {
   *
   *     // Walk through all the runs that intersect with the line in visual order
   *     SkScalar width = 0;
   *     SkScalar runOffset = 0;
   *     SkScalar totalWidth = 0;
   *     auto textRange = includingGhostSpaces ? this->textWithNewlines() : this->trimmedText();
   *
   *     if (this->ellipsis() != nullptr && fOwner->paragraphStyle().getTextDirection() == TextDirection::kRtl) {
   *         runOffset = this->ellipsis()->offset().fX;
   *         if (visitor(ellipsis(), runOffset, ellipsis()->textRange(), &width)) {
   *         }
   *     }
   *
   *     for (auto& runIndex : fRunsInVisualOrder) {
   *
   *         const auto run = &this->fOwner->run(runIndex);
   *         auto lineIntersection = intersected(run->textRange(), textRange);
   *         if (lineIntersection.width() == 0 && this->width() != 0) {
   *             // TODO: deal with empty runs in a better way
   *             continue;
   *         }
   *         if (!run->leftToRight() && runOffset == 0 && includingGhostSpaces) {
   *             // runOffset does not take in account a possibility
   *             // that RTL run could start before the line (trailing spaces)
   *             // so we need to do runOffset -= "trailing whitespaces length"
   *             TextRange whitespaces = intersected(
   *                     TextRange(fTextExcludingSpaces.end, fTextIncludingNewlines.end), run->fTextRange);
   *             if (whitespaces.width() > 0) {
   *                 auto whitespacesLen = measureTextInsideOneRun(whitespaces, run, runOffset, 0, true, TextAdjustment::GlyphCluster).clip.width();
   *                 runOffset -= whitespacesLen;
   *             }
   *         }
   *         runOffset += width;
   *         totalWidth += width;
   *         if (!visitor(run, runOffset, lineIntersection, &width)) {
   *             return;
   *         }
   *     }
   *
   *     runOffset += width;
   *     totalWidth += width;
   *
   *     if (this->ellipsis() != nullptr && fOwner->paragraphStyle().getTextDirection() == TextDirection::kLtr) {
   *         if (visitor(ellipsis(), runOffset, ellipsis()->textRange(), &width)) {
   *             totalWidth += width;
   *         }
   *     }
   *
   *     if (!includingGhostSpaces && compareRound(totalWidth, this->width(), fOwner->getApplyRoundingHack()) != 0) {
   *     // This is a very important assert!
   *     // It asserts that 2 different ways of calculation come with the same results
   *         SkDEBUGFAILF("ASSERT: %f != %f\n", totalWidth, this->width());
   *     }
   * }
   * ```
   */
  public fun iterateThroughSingleRunByStyles(
    textAdjustment: TextAdjustment,
    run: Run?,
    runOffset: SkScalar,
    textRange: TextRange,
    styleType: StyleType,
    visitor: RunStyleVisitor,
  ): Int {
    TODO("Implement iterateThroughSingleRunByStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar TextLine::iterateThroughSingleRunByStyles(TextAdjustment textAdjustment,
   *                                                    const Run* run,
   *                                                    SkScalar runOffset,
   *                                                    TextRange textRange,
   *                                                    StyleType styleType,
   *                                                    const RunStyleVisitor& visitor) const {
   *     auto correctContext = [&](TextRange textRange, SkScalar textOffsetInRun) -> ClipContext {
   *         auto result = this->measureTextInsideOneRun(
   *                 textRange, run, runOffset, textOffsetInRun, false, textAdjustment);
   *         if (styleType == StyleType::kDecorations) {
   *             // Decorations are drawn based on the real font metrics (regardless of styles and strut)
   *             result.clip.fTop = this->sizes().runTop(run, LineMetricStyle::CSS);
   *             result.clip.fBottom = result.clip.fTop +
   *                                   run->calculateHeight(LineMetricStyle::CSS, LineMetricStyle::CSS);
   *         }
   *         return result;
   *     };
   *
   *     if (run->fEllipsis) {
   *         // Extra efforts to get the ellipsis text style
   *         ClipContext clipContext = correctContext(run->textRange(), 0.0f);
   *         TextRange testRange(run->fClusterStart, run->fClusterStart + run->textRange().width());
   *         for (BlockIndex index = fBlockRange.start; index < fBlockRange.end; ++index) {
   *            auto block = fOwner->styles().begin() + index;
   *            auto intersect = intersected(block->fRange, testRange);
   *            if (intersect.width() > 0) {
   *                visitor(testRange, block->fStyle, clipContext);
   *                return run->advance().fX;
   *            }
   *         }
   *         SkASSERT(false);
   *     }
   *
   *     if (styleType == StyleType::kNone) {
   *         ClipContext clipContext = correctContext(textRange, 0.0f);
   *         // The placehoder can have height=0 or (exclusively) width=0 and still be a thing
   *         if (clipContext.clip.height() > 0.0f || clipContext.clip.width() > 0.0f) {
   *             visitor(textRange, TextStyle(), clipContext);
   *             return clipContext.clip.width();
   *         } else {
   *             return 0;
   *         }
   *     }
   *
   *     TextIndex start = EMPTY_INDEX;
   *     size_t size = 0;
   *     const TextStyle* prevStyle = nullptr;
   *     SkScalar textOffsetInRun = 0;
   *
   *     const BlockIndex blockRangeSize = fBlockRange.end - fBlockRange.start;
   *     for (BlockIndex index = 0; index <= blockRangeSize; ++index) {
   *
   *         TextRange intersect;
   *         TextStyle* style = nullptr;
   *         if (index < blockRangeSize) {
   *             auto block = fOwner->styles().begin() +
   *                  (run->leftToRight() ? fBlockRange.start + index : fBlockRange.end - index - 1);
   *
   *             // Get the text
   *             intersect = intersected(block->fRange, textRange);
   *             if (intersect.width() == 0) {
   *                 if (start == EMPTY_INDEX) {
   *                     // This style is not applicable to the text yet
   *                     continue;
   *                 } else {
   *                     // We have found all the good styles already
   *                     // but we need to process the last one of them
   *                     intersect = TextRange(start, start + size);
   *                     index = fBlockRange.end;
   *                 }
   *             } else {
   *                 // Get the style
   *                 style = &block->fStyle;
   *                 if (start != EMPTY_INDEX && style->matchOneAttribute(styleType, *prevStyle)) {
   *                     size += intersect.width();
   *                     // RTL text intervals move backward
   *                     start = std::min(intersect.start, start);
   *                     continue;
   *                 } else if (start == EMPTY_INDEX ) {
   *                     // First time only
   *                     prevStyle = style;
   *                     size = intersect.width();
   *                     start = intersect.start;
   *                     continue;
   *                 }
   *             }
   *         } else if (prevStyle != nullptr) {
   *             // This is the last style
   *         } else {
   *             break;
   *         }
   *
   *         // We have the style and the text
   *         auto runStyleTextRange = TextRange(start, start + size);
   *         ClipContext clipContext = correctContext(runStyleTextRange, textOffsetInRun);
   *         textOffsetInRun += clipContext.clip.width();
   *         if (clipContext.clip.height() == 0) {
   *             continue;
   *         }
   *         visitor(runStyleTextRange, *prevStyle, clipContext);
   *
   *         // Start all over again
   *         prevStyle = style;
   *         start = intersect.start;
   *         size = intersect.width();
   *     }
   *     return textOffsetInRun;
   * }
   * ```
   */
  public fun iterateThroughClustersInGlyphsOrder(
    reverse: Boolean,
    includeGhosts: Boolean,
    visitor: ClustersVisitor,
  ) {
    TODO("Implement iterateThroughClustersInGlyphsOrder")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::iterateThroughClustersInGlyphsOrder(bool reversed,
   *                                                    bool includeGhosts,
   *                                                    const ClustersVisitor& visitor) const {
   *     // Walk through the clusters in the logical order (or reverse)
   *     SkSpan<const size_t> runs(fRunsInVisualOrder.data(), fRunsInVisualOrder.size());
   *     bool ignore = false;
   *     ClusterIndex index = 0;
   *     directional_for_each(runs, !reversed, [&](decltype(runs[0]) r) {
   *         if (ignore) return;
   *         auto run = this->fOwner->run(r);
   *         auto trimmedRange = fClusterRange.intersection(run.clusterRange());
   *         auto trailedRange = fGhostClusterRange.intersection(run.clusterRange());
   *         SkASSERT(trimmedRange.start == trailedRange.start);
   *
   *         auto trailed = fOwner->clusters(trailedRange);
   *         auto trimmed = fOwner->clusters(trimmedRange);
   *         directional_for_each(trailed, reversed != run.leftToRight(), [&](Cluster& cluster) {
   *             if (ignore) return;
   *             bool ghost =  &cluster >= trimmed.data() + trimmed.size();
   *             if (!includeGhosts && ghost) {
   *                 return;
   *             }
   *             if (!visitor(&cluster, index++, ghost)) {
   *
   *                 ignore = true;
   *                 return;
   *             }
   *         });
   *     });
   * }
   * ```
   */
  public fun format(align: TextAlign, maxWidth: SkScalar) {
    TODO("Implement format")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::format(TextAlign align, SkScalar maxWidth) {
   *     SkScalar delta = maxWidth - this->width();
   *     if (delta <= 0) {
   *         return;
   *     }
   *
   *     // We do nothing for left align
   *     if (align == TextAlign::kJustify) {
   *         if (!this->endsWithHardLineBreak()) {
   *             this->justify(maxWidth);
   *         } else if (fOwner->paragraphStyle().getTextDirection() == TextDirection::kRtl) {
   *             // Justify -> Right align
   *             fShift = delta;
   *         }
   *     } else if (align == TextAlign::kRight) {
   *         fShift = delta;
   *     } else if (align == TextAlign::kCenter) {
   *         fShift = delta / 2;
   *     }
   * }
   * ```
   */
  public fun paint(
    painter: ParagraphPainter?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::paint(ParagraphPainter* painter, SkScalar x, SkScalar y) {
   *     if (fHasBackground) {
   *         this->iterateThroughVisualRuns(false,
   *             [painter, x, y, this]
   *             (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *                 *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *                 TextAdjustment::GlyphCluster, run, runOffsetInLine, textRange, StyleType::kBackground,
   *                 [painter, x, y, this](TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *                     this->paintBackground(painter, x, y, textRange, style, context);
   *                 });
   *             return true;
   *             });
   *     }
   *
   *     if (fHasShadows) {
   *         this->iterateThroughVisualRuns(false,
   *             [painter, x, y, this]
   *             (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *             *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *                 TextAdjustment::GlyphCluster, run, runOffsetInLine, textRange, StyleType::kShadow,
   *                 [painter, x, y, this]
   *                 (TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *                     this->paintShadow(painter, x, y, textRange, style, context);
   *                 });
   *             return true;
   *             });
   *     }
   *
   *     this->ensureTextBlobCachePopulated();
   *
   *     for (auto& record : fTextBlobCache) {
   *         record.paint(painter, x, y);
   *     }
   *
   *     if (fHasDecorations) {
   *         this->iterateThroughVisualRuns(false,
   *             [painter, x, y, this]
   *             (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *                 *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *                 TextAdjustment::GlyphCluster, run, runOffsetInLine, textRange, StyleType::kDecorations,
   *                 [painter, x, y, this]
   *                 (TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *                     this->paintDecorations(painter, x, y, textRange, style, context);
   *                 });
   *                 return true;
   *         });
   *     }
   * }
   * ```
   */
  public fun visit(x: SkScalar, y: SkScalar) {
    TODO("Implement visit")
  }

  /**
   * C++ original:
   * ```cpp
   * void visit(SkScalar x, SkScalar y)
   * ```
   */
  public fun ensureTextBlobCachePopulated() {
    TODO("Implement ensureTextBlobCachePopulated")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::ensureTextBlobCachePopulated() {
   *     if (fTextBlobCachePopulated) {
   *         return;
   *     }
   *     if (fBlockRange.width() == 1 &&
   *         fRunsInVisualOrder.size() == 1 &&
   *         fEllipsis == nullptr &&
   *         fOwner->run(fRunsInVisualOrder[0]).placeholderStyle() == nullptr) {
   *         if (fClusterRange.width() == 0) {
   *             return;
   *         }
   *         // Most common and most simple case
   *         const auto& style = fOwner->block(fBlockRange.start).fStyle;
   *         const auto& run = fOwner->run(fRunsInVisualOrder[0]);
   *         auto clip = SkRect::MakeXYWH(0.0f, this->sizes().runTop(&run, this->fAscentStyle),
   *                                      fAdvance.fX,
   *                                      run.calculateHeight(this->fAscentStyle, this->fDescentStyle));
   *
   *         auto& start = fOwner->cluster(fClusterRange.start);
   *         auto& end = fOwner->cluster(fClusterRange.end - 1);
   *         SkASSERT(start.runIndex() == end.runIndex());
   *         GlyphRange glyphs;
   *         if (run.leftToRight()) {
   *             glyphs = GlyphRange(start.startPos(),
   *                                 end.isHardBreak() ? end.startPos() : end.endPos());
   *         } else {
   *             glyphs = GlyphRange(end.startPos(),
   *                                 start.isHardBreak() ? start.startPos() : start.endPos());
   *         }
   *         ClipContext context = {/*run=*/&run,
   *                                /*pos=*/glyphs.start,
   *                                /*size=*/glyphs.width(),
   *                                /*fTextShift=*/-run.positionX(glyphs.start), // starting position
   *                                /*clip=*/clip,                               // entire line
   *                                /*fExcludedTrailingSpaces=*/0.0f,            // no need for that
   *                                /*clippingNeeded=*/false};                   // no need for that
   *         this->buildTextBlob(fTextExcludingSpaces, style, context);
   *     } else {
   *         this->iterateThroughVisualRuns(false,
   *            [this](const Run* run,
   *                   SkScalar runOffsetInLine,
   *                   TextRange textRange,
   *                   SkScalar* runWidthInLine) {
   *                if (run->placeholderStyle() != nullptr) {
   *                    *runWidthInLine = run->advance().fX;
   *                    return true;
   *                }
   *                *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *                    TextAdjustment::GlyphCluster,
   *                    run,
   *                    runOffsetInLine,
   *                    textRange,
   *                    StyleType::kForeground,
   *                    [this](TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *                        this->buildTextBlob(textRange, style, context);
   *                    });
   *                return true;
   *            });
   *     }
   *     fTextBlobCachePopulated = true;
   * }
   * ```
   */
  public fun createEllipsis(
    maxWidth: SkScalar,
    ellipsis: String,
    ltr: Boolean,
  ) {
    TODO("Implement createEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::createEllipsis(SkScalar maxWidth, const SkString& ellipsis, bool) {
   *     // Replace some clusters with the ellipsis
   *     // Go through the clusters in the reverse logical order
   *     // taking off cluster by cluster until the ellipsis fits
   *     SkScalar width = fAdvance.fX;
   *     RunIndex lastRun = EMPTY_RUN;
   *     std::unique_ptr<Run> ellipsisRun;
   *     for (auto clusterIndex = fGhostClusterRange.end; clusterIndex > fGhostClusterRange.start; --clusterIndex) {
   *         auto& cluster = fOwner->cluster(clusterIndex - 1);
   *         // Shape the ellipsis if the run has changed
   *         if (lastRun != cluster.runIndex()) {
   *             ellipsisRun = this->shapeEllipsis(ellipsis, &cluster);
   *             if (ellipsisRun->advance().fX > maxWidth) {
   *                 // Ellipsis is bigger than the entire line; no way we can add it at all
   *                 // BUT! We can keep scanning in case the next run will give us better results
   *                 lastRun = EMPTY_RUN;
   *                 continue;
   *             } else {
   *                 // We may need to continue
   *                 lastRun = cluster.runIndex();
   *             }
   *         }
   *         // See if it fits
   *         if (width + ellipsisRun->advance().fX > maxWidth) {
   *             width -= cluster.width();
   *             // Continue if the ellipsis does not fit
   *             continue;
   *         }
   *         // We found enough room for the ellipsis
   *         fAdvance.fX = width;
   *         fEllipsis = std::move(ellipsisRun);
   *         fEllipsis->setOwner(fOwner);
   *
   *         // Let's update the line
   *         fClusterRange.end = clusterIndex;
   *         fGhostClusterRange.end = fClusterRange.end;
   *         fEllipsis->fClusterStart = cluster.textRange().start;
   *         fText.end = cluster.textRange().end;
   *         fTextIncludingNewlines.end = cluster.textRange().end;
   *         fTextExcludingSpaces.end = cluster.textRange().end;
   *         break;
   *     }
   *
   *     if (!fEllipsis) {
   *         // Weird situation: ellipsis does not fit; no ellipsis then
   *         fClusterRange.end = fClusterRange.start;
   *         fGhostClusterRange.end = fClusterRange.start;
   *         fText.end = fText.start;
   *         fTextIncludingNewlines.end = fTextIncludingNewlines.start;
   *         fTextExcludingSpaces.end = fTextExcludingSpaces.start;
   *         fAdvance.fX = 0;
   *     }
   * }
   * ```
   */
  public fun scanStyles(style: StyleType, visitor: RunStyleVisitor) {
    TODO("Implement scanStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::scanStyles(StyleType styleType, const RunStyleVisitor& visitor) {
   *     if (this->empty()) {
   *         return;
   *     }
   *
   *     this->iterateThroughVisualRuns(
   *             false,
   *             [this, visitor, styleType](
   *                     const Run* run, SkScalar runOffset, TextRange textRange, SkScalar* width) {
   *                 *width = this->iterateThroughSingleRunByStyles(
   *                         TextAdjustment::GlyphCluster,
   *                         run,
   *                         runOffset,
   *                         textRange,
   *                         styleType,
   *                         [visitor](TextRange textRange,
   *                                   const TextStyle& style,
   *                                   const ClipContext& context) {
   *                             visitor(textRange, style, context);
   *                         });
   *                 return true;
   *             });
   * }
   * ```
   */
  public fun setMaxRunMetrics(metrics: InternalLineMetrics) {
    TODO("Implement setMaxRunMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMaxRunMetrics(const InternalLineMetrics& metrics) { fMaxRunMetrics = metrics; }
   * ```
   */
  public fun getMaxRunMetrics(): Int {
    TODO("Implement getMaxRunMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics getMaxRunMetrics() const { return fMaxRunMetrics; }
   * ```
   */
  public fun isFirstLine(): Boolean {
    TODO("Implement isFirstLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextLine::isFirstLine() const {
   *     return this == &fOwner->lines().front();
   * }
   * ```
   */
  public fun isLastLine(): Boolean {
    TODO("Implement isLastLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextLine::isLastLine() const {
   *     return this == &fOwner->lines().back();
   * }
   * ```
   */
  public fun getRectsForRange(
    textRange: TextRange,
    rectHeightStyle: RectHeightStyle,
    rectWidthStyle: RectWidthStyle,
    boxes: List<TextBox>,
  ) {
    TODO("Implement getRectsForRange")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::getRectsForRange(TextRange textRange0,
   *                                 RectHeightStyle rectHeightStyle,
   *                                 RectWidthStyle rectWidthStyle,
   *                                 std::vector<TextBox>& boxes) const
   * {
   *     const Run* lastRun = nullptr;
   *     auto startBox = boxes.size();
   *     this->iterateThroughVisualRuns(true,
   *         [textRange0, rectHeightStyle, rectWidthStyle, &boxes, &lastRun, startBox, this]
   *         (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *         *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *         TextAdjustment::GraphemeGluster, run, runOffsetInLine, textRange, StyleType::kNone,
   *         [run, runOffsetInLine, textRange0, rectHeightStyle, rectWidthStyle, &boxes, &lastRun, startBox, this]
   *         (TextRange textRange, const TextStyle& style, const TextLine::ClipContext& lineContext) {
   *
   *             auto intersect = textRange * textRange0;
   *             if (intersect.empty()) {
   *                 return true;
   *             }
   *
   *             auto paragraphStyle = fOwner->paragraphStyle();
   *
   *             // Found a run that intersects with the text
   *             auto context = this->measureTextInsideOneRun(
   *                     intersect, run, runOffsetInLine, 0, true, TextAdjustment::GraphemeGluster);
   *             SkRect clip = context.clip;
   *             clip.offset(lineContext.fTextShift - context.fTextShift, 0);
   *
   *             switch (rectHeightStyle) {
   *                 case RectHeightStyle::kMax:
   *                     // TODO: Change it once flutter rolls into google3
   *                     //  (probably will break things if changed before)
   *                     clip.fBottom = this->height();
   *                     clip.fTop = this->sizes().delta();
   *                     break;
   *                 case RectHeightStyle::kIncludeLineSpacingTop: {
   *                     clip.fBottom = this->height();
   *                     clip.fTop = this->sizes().delta();
   *                     auto verticalShift = this->sizes().rawAscent() - this->sizes().ascent();
   *                     if (isFirstLine()) {
   *                         clip.fTop += verticalShift;
   *                     }
   *                     break;
   *                 }
   *                 case RectHeightStyle::kIncludeLineSpacingMiddle: {
   *                     clip.fBottom = this->height();
   *                     clip.fTop = this->sizes().delta();
   *                     auto verticalShift = this->sizes().rawAscent() - this->sizes().ascent();
   *                     clip.offset(0, verticalShift / 2.0);
   *                     if (isFirstLine()) {
   *                         clip.fTop += verticalShift / 2.0;
   *                     }
   *                     if (isLastLine()) {
   *                         clip.fBottom -= verticalShift / 2.0;
   *                     }
   *                     break;
   *                  }
   *                 case RectHeightStyle::kIncludeLineSpacingBottom: {
   *                     clip.fBottom = this->height();
   *                     clip.fTop = this->sizes().delta();
   *                     auto verticalShift = this->sizes().rawAscent() - this->sizes().ascent();
   *                     clip.offset(0, verticalShift);
   *                     if (isLastLine()) {
   *                         clip.fBottom -= verticalShift;
   *                     }
   *                     break;
   *                 }
   *                 case RectHeightStyle::kStrut: {
   *                     const auto& strutStyle = paragraphStyle.getStrutStyle();
   *                     if (strutStyle.getStrutEnabled()
   *                         && strutStyle.getFontSize() > 0) {
   *                         auto strutMetrics = fOwner->strutMetrics();
   *                         auto top = this->baseline();
   *                         clip.fTop = top + strutMetrics.ascent();
   *                         clip.fBottom = top + strutMetrics.descent();
   *                     }
   *                 }
   *                 break;
   *                 case RectHeightStyle::kTight: {
   *                     if (run->fHeightMultiplier <= 0) {
   *                         break;
   *                     }
   *                     const auto effectiveBaseline = this->baseline() + this->sizes().delta();
   *                     clip.fTop = effectiveBaseline + run->ascent();
   *                     clip.fBottom = effectiveBaseline + run->descent();
   *                 }
   *                 break;
   *                 default:
   *                     SkASSERT(false);
   *                 break;
   *             }
   *
   *             // Separate trailing spaces and move them in the default order of the paragraph
   *             // in case the run order and the paragraph order don't match
   *             SkRect trailingSpaces = SkRect::MakeEmpty();
   *             if (this->trimmedText().end <this->textWithNewlines().end && // Line has trailing space
   *                 this->textWithNewlines().end == intersect.end &&         // Range is at the end of the line
   *                 this->trimmedText().end > intersect.start)               // Range has more than just spaces
   *             {
   *                 auto delta = this->spacesWidth();
   *                 trailingSpaces = SkRect::MakeXYWH(0, 0, 0, 0);
   *                 // There are trailing spaces in this run
   *                 if (paragraphStyle.getTextAlign() == TextAlign::kJustify && isLastLine())
   *                 {
   *                     // TODO: this is just a patch. Make it right later (when it's clear what and how)
   *                     trailingSpaces = clip;
   *                     if(run->leftToRight()) {
   *                         trailingSpaces.fLeft = this->width();
   *                         clip.fRight = this->width();
   *                     } else {
   *                         trailingSpaces.fRight = 0;
   *                         clip.fLeft = 0;
   *                     }
   *                 } else if (paragraphStyle.getTextDirection() == TextDirection::kRtl &&
   *                     !run->leftToRight())
   *                 {
   *                     // Split
   *                     trailingSpaces = clip;
   *                     trailingSpaces.fLeft = - delta;
   *                     trailingSpaces.fRight = 0;
   *                     clip.fLeft += delta;
   *                 } else if (paragraphStyle.getTextDirection() == TextDirection::kLtr &&
   *                     run->leftToRight())
   *                 {
   *                     // Split
   *                     trailingSpaces = clip;
   *                     trailingSpaces.fLeft = this->width();
   *                     trailingSpaces.fRight = trailingSpaces.fLeft + delta;
   *                     clip.fRight -= delta;
   *                 }
   *             }
   *
   *             clip.offset(this->offset());
   *             if (trailingSpaces.width() > 0) {
   *                 trailingSpaces.offset(this->offset());
   *             }
   *
   *             // Check if we can merge two boxes instead of adding a new one
   *             auto merge = [&lastRun, &context, &boxes](SkRect clip) {
   *                 bool mergedBoxes = false;
   *                 if (!boxes.empty() &&
   *                     lastRun != nullptr &&
   *                     context.run->leftToRight() == lastRun->leftToRight() &&
   *                     lastRun->placeholderStyle() == nullptr &&
   *                     context.run->placeholderStyle() == nullptr &&
   *                     nearlyEqual(lastRun->heightMultiplier(),
   *                                 context.run->heightMultiplier()) &&
   *                     lastRun->font() == context.run->font())
   *                 {
   *                     auto& lastBox = boxes.back();
   *                     if (nearlyEqual(lastBox.rect.fTop, clip.fTop) &&
   *                         nearlyEqual(lastBox.rect.fBottom, clip.fBottom) &&
   *                             (nearlyEqual(lastBox.rect.fLeft, clip.fRight) ||
   *                              nearlyEqual(lastBox.rect.fRight, clip.fLeft)))
   *                     {
   *                         lastBox.rect.fLeft = std::min(lastBox.rect.fLeft, clip.fLeft);
   *                         lastBox.rect.fRight = std::max(lastBox.rect.fRight, clip.fRight);
   *                         mergedBoxes = true;
   *                     }
   *                 }
   *                 lastRun = context.run;
   *                 return mergedBoxes;
   *             };
   *
   *             if (!merge(clip)) {
   *                 boxes.emplace_back(clip, context.run->getTextDirection());
   *             }
   *             if (!nearlyZero(trailingSpaces.width()) && !merge(trailingSpaces)) {
   *                 boxes.emplace_back(trailingSpaces, paragraphStyle.getTextDirection());
   *             }
   *
   *             if (rectWidthStyle == RectWidthStyle::kMax && !isLastLine()) {
   *                 // Align the very left/right box horizontally
   *                 auto lineStart = this->offset().fX;
   *                 auto lineEnd = this->offset().fX + this->width();
   *                 auto left = boxes[startBox];
   *                 auto right = boxes.back();
   *                 if (left.rect.fLeft > lineStart && left.direction == TextDirection::kRtl) {
   *                     left.rect.fRight = left.rect.fLeft;
   *                     left.rect.fLeft = 0;
   *                     boxes.insert(boxes.begin() + startBox + 1, left);
   *                 }
   *                 if (right.direction == TextDirection::kLtr &&
   *                     right.rect.fRight >= lineEnd &&
   *                     right.rect.fRight < fOwner->widthWithTrailingSpaces()) {
   *                     right.rect.fLeft = right.rect.fRight;
   *                     right.rect.fRight = fOwner->widthWithTrailingSpaces();
   *                     boxes.emplace_back(right);
   *                 }
   *             }
   *
   *             return true;
   *         });
   *         return true;
   *     });
   *     if (fOwner->getApplyRoundingHack()) {
   *         for (auto& r : boxes) {
   *             r.rect.fLeft = littleRound(r.rect.fLeft);
   *             r.rect.fRight = littleRound(r.rect.fRight);
   *             r.rect.fTop = littleRound(r.rect.fTop);
   *             r.rect.fBottom = littleRound(r.rect.fBottom);
   *         }
   *     }
   * }
   * ```
   */
  public fun getRectsForPlaceholders(boxes: List<TextBox>) {
    TODO("Implement getRectsForPlaceholders")
  }

  /**
   * C++ original:
   * ```cpp
   * void getRectsForPlaceholders(std::vector<TextBox>& boxes)
   * ```
   */
  public fun getGlyphPositionAtCoordinate(dx: SkScalar): Int {
    TODO("Implement getGlyphPositionAtCoordinate")
  }

  /**
   * C++ original:
   * ```cpp
   * PositionWithAffinity TextLine::getGlyphPositionAtCoordinate(SkScalar dx) {
   *
   *     if (SkScalarNearlyZero(this->width()) && SkScalarNearlyZero(this->spacesWidth())) {
   *         // TODO: this is one of the flutter changes that have to go away eventually
   *         //  Empty line is a special case in txtlib (but only when there are no spaces, too)
   *         auto utf16Index = fOwner->getUTF16Index(this->fTextExcludingSpaces.end);
   *         return { SkToS32(utf16Index) , kDownstream };
   *     }
   *
   *     PositionWithAffinity result(0, Affinity::kDownstream);
   *     this->iterateThroughVisualRuns(true,
   *         [this, dx, &result]
   *         (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *             if (run->isEllipsis()) {
   *                 auto utf16Index = fOwner->getUTF16Index(this->fText.end);
   *                 result = { SkToS32(utf16Index) , kDownstream };
   *                 return false;
   *             }
   *
   *             bool keepLooking = true;
   *             *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *             TextAdjustment::GraphemeGluster, run, runOffsetInLine, textRange, StyleType::kNone,
   *             [this, run, dx, &result, &keepLooking]
   *             (TextRange textRange, const TextStyle& style, const TextLine::ClipContext& context0) {
   *
   *                 SkScalar offsetX = this->offset().fX;
   *                 ClipContext context = context0;
   *
   *                 // Correct the clip size because libtxt counts trailing spaces
   *                 if (run->leftToRight()) {
   *                     context.clip.fRight += context.fExcludedTrailingSpaces; // extending clip to the right
   *                 } else {
   *                     // Clip starts from 0; we cannot extend it to the left from that
   *                 }
   *                 // However, we need to offset the clip
   *                 context.clip.offset(offsetX, 0.0f);
   *
   *                 // This patch will help us to avoid a floating point error
   *                 if (SkScalarNearlyEqual(context.clip.fRight, dx, 0.01f)) {
   *                     context.clip.fRight = dx;
   *                 }
   *
   *                 if (dx <= context.clip.fLeft) {
   *                     // All the other runs are placed right of this one
   *                     auto utf16Index = fOwner->getUTF16Index(context.run->globalClusterIndex(context.pos));
   *                     if (run->leftToRight()) {
   *                         result = { SkToS32(utf16Index), kDownstream};
   *                         keepLooking = false;
   *                     } else {
   *                         result = { SkToS32(utf16Index + 1), kUpstream};
   *                         // If we haven't reached the end of the run we need to keep looking
   *                         keepLooking = context.pos != 0;
   *                     }
   *                     // For RTL we go another way
   *                     return !run->leftToRight();
   *                 }
   *
   *                 if (dx >= context.clip.fRight) {
   *                     // We have to keep looking ; just in case keep the last one as the closest
   *                     auto utf16Index = fOwner->getUTF16Index(context.run->globalClusterIndex(context.pos + context.size));
   *                     if (run->leftToRight()) {
   *                         result = {SkToS32(utf16Index), kUpstream};
   *                     } else {
   *                         result = {SkToS32(utf16Index), kDownstream};
   *                     }
   *                     // For RTL we go another way
   *                     return run->leftToRight();
   *                 }
   *
   *                 // So we found the run that contains our coordinates
   *                 // Find the glyph position in the run that is the closest left of our point
   *                 // TODO: binary search
   *                 size_t found = context.pos;
   *                 for (size_t index = context.pos; index < context.pos + context.size; ++index) {
   *                     // TODO: this rounding is done to match Flutter tests. Must be removed..
   *                     auto end = context.run->positionX(index) + context.fTextShift + offsetX;
   *                     if (fOwner->getApplyRoundingHack()) {
   *                         end = littleRound(end);
   *                     }
   *                     if (end > dx) {
   *                         break;
   *                     } else if (end == dx && !context.run->leftToRight()) {
   *                         // When we move RTL variable end points to the beginning of the code point which is included
   *                         found = index;
   *                         break;
   *                     }
   *                     found = index;
   *                 }
   *
   *                 SkScalar glyphemePosLeft = context.run->positionX(found) + context.fTextShift + offsetX;
   *                 SkScalar glyphemesWidth = context.run->positionX(found + 1) - context.run->positionX(found);
   *
   *                 // Find the grapheme range that contains the point
   *                 auto clusterIndex8 = context.run->globalClusterIndex(found);
   *                 auto clusterEnd8 = context.run->globalClusterIndex(found + 1);
   *                 auto graphemes = fOwner->countSurroundingGraphemes({clusterIndex8, clusterEnd8});
   *
   *                 SkScalar center = glyphemePosLeft + glyphemesWidth / 2;
   *                 if (graphemes.size() > 1) {
   *                     // Calculate the position proportionally based on grapheme count
   *                     SkScalar averageGraphemeWidth = glyphemesWidth / graphemes.size();
   *                     SkScalar delta = dx - glyphemePosLeft;
   *                     int graphemeIndex = SkScalarNearlyZero(averageGraphemeWidth)
   *                                          ? 0
   *                                          : SkScalarFloorToInt(delta / averageGraphemeWidth);
   *                     auto graphemeCenter = glyphemePosLeft + graphemeIndex * averageGraphemeWidth +
   *                                           averageGraphemeWidth / 2;
   *                     auto graphemeUtf8Index = graphemes[graphemeIndex];
   *                     if ((dx < graphemeCenter) == context.run->leftToRight()) {
   *                         size_t utf16Index = fOwner->getUTF16Index(graphemeUtf8Index);
   *                         result = { SkToS32(utf16Index), kDownstream };
   *                     } else {
   *                         size_t utf16Index = fOwner->getUTF16Index(graphemeUtf8Index + 1);
   *                         result = { SkToS32(utf16Index), kUpstream };
   *                     }
   *                     // Keep UTF16 index as is
   *                 } else if ((dx < center) == context.run->leftToRight()) {
   *                     size_t utf16Index = fOwner->getUTF16Index(clusterIndex8);
   *                     result = { SkToS32(utf16Index), kDownstream };
   *                 } else {
   *                     size_t utf16Index = context.run->leftToRight()
   *                                                 ? fOwner->getUTF16Index(clusterEnd8)
   *                                                 : fOwner->getUTF16Index(clusterIndex8) + 1;
   *                     result = { SkToS32(utf16Index), kUpstream };
   *                 }
   *
   *                 return keepLooking = false;
   *
   *             });
   *             return keepLooking;
   *         }
   *     );
   *     return result;
   * }
   * ```
   */
  public fun measureTextInsideOneRun(
    textRange: TextRange,
    run: Run?,
    runOffsetInLine: SkScalar,
    textOffsetInRunInLine: SkScalar,
    includeGhostSpaces: Boolean,
    textAdjustment: TextAdjustment,
  ): ClipContext {
    TODO("Implement measureTextInsideOneRun")
  }

  /**
   * C++ original:
   * ```cpp
   * TextLine::ClipContext TextLine::measureTextInsideOneRun(TextRange textRange,
   *                                                         const Run* run,
   *                                                         SkScalar runOffsetInLine,
   *                                                         SkScalar textOffsetInRunInLine,
   *                                                         bool includeGhostSpaces,
   *                                                         TextAdjustment textAdjustment) const {
   *     ClipContext result = { run, 0, run->size(), 0, SkRect::MakeEmpty(), 0, false };
   *
   *     if (run->fEllipsis) {
   *         // Both ellipsis and placeholders can only be measured as one glyph
   *         result.fTextShift = runOffsetInLine;
   *         result.clip = SkRect::MakeXYWH(runOffsetInLine,
   *                                        sizes().runTop(run, this->fAscentStyle),
   *                                        run->advance().fX,
   *                                        run->calculateHeight(this->fAscentStyle,this->fDescentStyle));
   *         return result;
   *     } else if (run->isPlaceholder()) {
   *         result.fTextShift = runOffsetInLine;
   *         if (SkIsFinite(run->fFontMetrics.fAscent)) {
   *           result.clip = SkRect::MakeXYWH(runOffsetInLine,
   *                                          sizes().runTop(run, this->fAscentStyle),
   *                                          run->advance().fX,
   *                                          run->calculateHeight(this->fAscentStyle,this->fDescentStyle));
   *         } else {
   *             result.clip = SkRect::MakeXYWH(runOffsetInLine, run->fFontMetrics.fAscent, run->advance().fX, 0);
   *         }
   *         return result;
   *     } else if (textRange.empty()) {
   *         return result;
   *     }
   *
   *     TextRange originalTextRange(textRange); // We need it for proportional measurement
   *     // Find [start:end] clusters for the text
   *     while (true) {
   *         // Update textRange by cluster edges (shift start up to the edge of the cluster)
   *         // TODO: remove this limitation?
   *         TextRange updatedTextRange;
   *         bool found;
   *         std::tie(found, updatedTextRange.start, updatedTextRange.end) =
   *                                         run->findLimitingGlyphClusters(textRange);
   *         if (!found) {
   *             return result;
   *         }
   *
   *         if ((textAdjustment & TextAdjustment::Grapheme) == 0) {
   *             textRange = updatedTextRange;
   *             break;
   *         }
   *
   *         // Update text range by grapheme edges (shift start up to the edge of the grapheme)
   *         std::tie(found, updatedTextRange.start, updatedTextRange.end) =
   *                                     run->findLimitingGraphemes(updatedTextRange);
   *         if (updatedTextRange == textRange) {
   *             break;
   *         }
   *
   *         // Some clusters are inside graphemes and we need to adjust them
   *         //SkDebugf("Correct range: [%d:%d) -> [%d:%d)\n", textRange.start, textRange.end, startIndex, endIndex);
   *         textRange = updatedTextRange;
   *
   *         // Move the start until it's on the grapheme edge (and glypheme, too)
   *     }
   *     Cluster* start = &fOwner->cluster(fOwner->clusterIndex(textRange.start));
   *     Cluster* end = &fOwner->cluster(fOwner->clusterIndex(textRange.end - (textRange.width() == 0 ? 0 : 1)));
   *
   *     if (!run->leftToRight()) {
   *         std::swap(start, end);
   *     }
   *     result.pos = start->startPos();
   *     result.size = (end->isHardBreak() ? end->startPos() : end->endPos()) - start->startPos();
   *     auto textStartInRun = run->positionX(start->startPos());
   *     auto textStartInLine = runOffsetInLine + textOffsetInRunInLine;
   *     if (!run->leftToRight()) {
   *         std::swap(start, end);
   *     }
   * /*
   *     if (!run->fJustificationShifts.empty()) {
   *         SkDebugf("Justification for [%d:%d)\n", textRange.start, textRange.end);
   *         for (auto i = result.pos; i < result.pos + result.size; ++i) {
   *             auto j = run->fJustificationShifts[i];
   *             SkDebugf("[%d] = %f %f\n", i, j.fX, j.fY);
   *         }
   *     }
   * */
   *     // Calculate the clipping rectangle for the text with cluster edges
   *     // There are 2 cases:
   *     // EOL (when we expect the last cluster clipped without any spaces)
   *     // Anything else (when we want the cluster width contain all the spaces -
   *     // coming from letter spacing or word spacing or justification)
   *     result.clip =
   *             SkRect::MakeXYWH(0,
   *                              sizes().runTop(run, this->fAscentStyle),
   *                              run->calculateWidth(result.pos, result.pos + result.size, false),
   *                              run->calculateHeight(this->fAscentStyle,this->fDescentStyle));
   *
   *     // Correct the width in case the text edges don't match clusters
   *     // TODO: This is where we get smart about selecting a part of a cluster
   *     //  by shaping each grapheme separately and then use the result sizes
   *     //  to calculate the proportions
   *     auto leftCorrection = start->sizeToChar(originalTextRange.start);
   *     auto rightCorrection = end->sizeFromChar(originalTextRange.end - 1);
   *     /*
   *     SkDebugf("[%d: %d) => [%d: %d), @%d, %d: [%f:%f) + [%f:%f) = ", // جَآَهُ
   *              originalTextRange.start, originalTextRange.end, textRange.start, textRange.end,
   *              result.pos, result.size,
   *              result.clip.fLeft, result.clip.fRight, leftCorrection, rightCorrection);
   *      */
   *     result.clippingNeeded = leftCorrection != 0 || rightCorrection != 0;
   *     if (run->leftToRight()) {
   *         result.clip.fLeft += leftCorrection;
   *         result.clip.fRight -= rightCorrection;
   *         textStartInLine -= leftCorrection;
   *     } else {
   *         result.clip.fRight -= leftCorrection;
   *         result.clip.fLeft += rightCorrection;
   *         textStartInLine -= rightCorrection;
   *     }
   *
   *     result.clip.offset(textStartInLine, 0);
   *     //SkDebugf("@%f[%f:%f)\n", textStartInLine, result.clip.fLeft, result.clip.fRight);
   *
   *     if (compareRound(result.clip.fRight, fAdvance.fX, fOwner->getApplyRoundingHack()) > 0 && !includeGhostSpaces) {
   *         // There are few cases when we need it.
   *         // The most important one: we measure the text with spaces at the end (or at the beginning in RTL)
   *         // and we should ignore these spaces
   *         if (fOwner->paragraphStyle().getTextDirection() == TextDirection::kLtr) {
   *             // We only use this member for LTR
   *             result.fExcludedTrailingSpaces = std::max(result.clip.fRight - fAdvance.fX, 0.0f);
   *             result.clippingNeeded = true;
   *             result.clip.fRight = fAdvance.fX;
   *         }
   *     }
   *
   *     if (result.clip.width() < 0) {
   *         // Weird situation when glyph offsets move the glyph to the left
   *         // (happens with zalgo texts, for instance)
   *         result.clip.fRight = result.clip.fLeft;
   *     }
   *
   *     // The text must be aligned with the lineOffset
   *     result.fTextShift = textStartInLine - textStartInRun;
   *
   *     return result;
   * }
   * ```
   */
  public fun getMetrics(): Int {
    TODO("Implement getMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * LineMetrics TextLine::getMetrics() const {
   *     LineMetrics result;
   *     SkASSERT(fOwner);
   *
   *     // Fill out the metrics
   *     fOwner->ensureUTF16Mapping();
   *     result.fStartIndex = fOwner->getUTF16Index(fTextExcludingSpaces.start);
   *     result.fEndExcludingWhitespaces = fOwner->getUTF16Index(fTextExcludingSpaces.end);
   *     result.fEndIndex = fOwner->getUTF16Index(fText.end);
   *     result.fEndIncludingNewline = fOwner->getUTF16Index(fTextIncludingNewlines.end);
   *     result.fHardBreak = endsWithHardLineBreak();
   *     result.fAscent = - fMaxRunMetrics.ascent();
   *     result.fDescent = fMaxRunMetrics.descent();
   *     result.fUnscaledAscent = - fMaxRunMetrics.ascent(); // TODO: implement
   *     result.fHeight = fAdvance.fY;
   *     result.fWidth = fAdvance.fX;
   *     if (fOwner->getApplyRoundingHack()) {
   *         result.fHeight = littleRound(result.fHeight);
   *         result.fWidth = littleRound(result.fWidth);
   *     }
   *     result.fLeft = this->offset().fX;
   *     // This is Flutter definition of a baseline
   *     result.fBaseline = this->offset().fY + this->height() - this->sizes().descent();
   *     result.fLineNumber = this - fOwner->lines().data();
   *
   *     // Fill out the style parts
   *     this->iterateThroughVisualRuns(false,
   *         [this, &result]
   *         (const Run* run, SkScalar runOffsetInLine, TextRange textRange, SkScalar* runWidthInLine) {
   *         if (run->placeholderStyle() != nullptr) {
   *             *runWidthInLine = run->advance().fX;
   *             return true;
   *         }
   *         *runWidthInLine = this->iterateThroughSingleRunByStyles(
   *         TextAdjustment::GlyphCluster, run, runOffsetInLine, textRange, StyleType::kForeground,
   *         [&result, &run](TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *             SkFontMetrics fontMetrics;
   *             run->fFont.getMetrics(&fontMetrics);
   *             StyleMetrics styleMetrics(&style, fontMetrics);
   *             result.fLineMetrics.emplace(textRange.start, styleMetrics);
   *         });
   *         return true;
   *     });
   *
   *     return result;
   * }
   * ```
   */
  public fun extendHeight(context: ClipContext): Int {
    TODO("Implement extendHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect TextLine::extendHeight(const ClipContext& context) const {
   *     SkRect result = context.clip;
   *     result.fBottom += std::max(this->fMaxRunMetrics.height() - this->height(), 0.0f);
   *     return result;
   * }
   * ```
   */
  public fun shiftVertically(shift: SkScalar) {
    TODO("Implement shiftVertically")
  }

  /**
   * C++ original:
   * ```cpp
   * void shiftVertically(SkScalar shift) { fOffset.fY += shift; }
   * ```
   */
  public fun setAscentStyle(style: LineMetricStyle) {
    TODO("Implement setAscentStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAscentStyle(LineMetricStyle style) { fAscentStyle = style; }
   * ```
   */
  public fun setDescentStyle(style: LineMetricStyle) {
    TODO("Implement setDescentStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDescentStyle(LineMetricStyle style) { fDescentStyle = style; }
   * ```
   */
  public fun endsWithHardLineBreak(): Boolean {
    TODO("Implement endsWithHardLineBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextLine::endsWithHardLineBreak() const {
   *     // TODO: For some reason Flutter imagines a hard line break at the end of the last line.
   *     //  To be removed...
   *     return (fGhostClusterRange.width() > 0 && fOwner->cluster(fGhostClusterRange.end - 1).isHardBreak()) ||
   *            fEllipsis != nullptr ||
   *            fGhostClusterRange.end == fOwner->clusters().size() - 1;
   * }
   * ```
   */
  private fun shapeEllipsis(ellipsis: String, cluster: Cluster?): Int {
    TODO("Implement shapeEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Run> TextLine::shapeEllipsis(const SkString& ellipsis, const Cluster* cluster) {
   *
   *     class ShapeHandler final : public SkShaper::RunHandler {
   *     public:
   *         ShapeHandler(SkScalar lineHeight, bool useHalfLeading, SkScalar baselineShift, const SkString& ellipsis)
   *             : fRun(nullptr), fLineHeight(lineHeight), fUseHalfLeading(useHalfLeading), fBaselineShift(baselineShift), fEllipsis(ellipsis) {}
   *         std::unique_ptr<Run> run() & { return std::move(fRun); }
   *
   *     private:
   *         void beginLine() override {}
   *
   *         void runInfo(const RunInfo&) override {}
   *
   *         void commitRunInfo() override {}
   *
   *         Buffer runBuffer(const RunInfo& info) override {
   *             SkASSERT(!fRun);
   *             fRun = std::make_unique<Run>(nullptr, info, 0, fLineHeight, fUseHalfLeading, fBaselineShift, 0, 0);
   *             return fRun->newRunBuffer();
   *         }
   *
   *         void commitRunBuffer(const RunInfo& info) override {
   *             fRun->fAdvance.fX = info.fAdvance.fX;
   *             fRun->fAdvance.fY = fRun->advance().fY;
   *             fRun->fPlaceholderIndex = std::numeric_limits<size_t>::max();
   *             fRun->fEllipsis = true;
   *         }
   *
   *         void commitLine() override {}
   *
   *         std::unique_ptr<Run> fRun;
   *         SkScalar fLineHeight;
   *         bool fUseHalfLeading;
   *         SkScalar fBaselineShift;
   *         SkString fEllipsis;
   *     };
   *
   *     const Run& run = cluster->run();
   *     TextStyle textStyle = fOwner->paragraphStyle().getTextStyle();
   *     for (auto i = fBlockRange.start; i < fBlockRange.end; ++i) {
   *         auto& block = fOwner->block(i);
   *         if (run.leftToRight() && cluster->textRange().end <= block.fRange.end) {
   *             textStyle = block.fStyle;
   *             break;
   *         } else if (!run.leftToRight() && cluster->textRange().start <= block.fRange.end) {
   *             textStyle = block.fStyle;
   *             break;
   *         }
   *     }
   *
   *     auto shaped = [&](sk_sp<SkTypeface> typeface, sk_sp<SkFontMgr> fallback) -> std::unique_ptr<Run> {
   *         ShapeHandler handler(run.heightMultiplier(), run.useHalfLeading(), run.baselineShift(), ellipsis);
   *         SkFont font(std::move(typeface), textStyle.getFontSize());
   *         font.setEdging(textStyle.getFontEdging());
   *         font.setHinting(textStyle.getFontHinting());
   *         font.setSubpixel(textStyle.getSubpixel());
   *
   *         std::unique_ptr<SkShaper> shaper = SkShapers::HB::ShapeDontWrapOrReorder(
   *                 fOwner->getUnicode(), fallback ? fallback : SkFontMgr::RefEmpty());
   *
   *         const SkBidiIterator::Level defaultLevel = SkBidiIterator::kLTR;
   *         const char* utf8 = ellipsis.c_str();
   *         size_t utf8Bytes = ellipsis.size();
   *
   *         std::unique_ptr<SkShaper::BiDiRunIterator> bidi = SkShapers::unicode::BidiRunIterator(
   *                 fOwner->getUnicode(), utf8, utf8Bytes, defaultLevel);
   *         SkASSERT(bidi);
   *
   *         std::unique_ptr<SkShaper::LanguageRunIterator> language =
   *                 SkShaper::MakeStdLanguageRunIterator(utf8, utf8Bytes);
   *         SkASSERT(language);
   *
   *         std::unique_ptr<SkShaper::ScriptRunIterator> script =
   *                 SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes);
   *         SkASSERT(script);
   *
   *         std::unique_ptr<SkShaper::FontRunIterator> fontRuns = SkShaper::MakeFontMgrRunIterator(
   *                 utf8, utf8Bytes, font, fallback ? fallback : SkFontMgr::RefEmpty());
   *         SkASSERT(fontRuns);
   *
   *         shaper->shape(utf8,
   *                       utf8Bytes,
   *                       *fontRuns,
   *                       *bidi,
   *                       *script,
   *                       *language,
   *                       nullptr,
   *                       0,
   *                       std::numeric_limits<SkScalar>::max(),
   *                       &handler);
   *         auto ellipsisRun = handler.run();
   *         ellipsisRun->fTextRange = TextRange(0, ellipsis.size());
   *         ellipsisRun->fOwner = fOwner;
   *         return ellipsisRun;
   *     };
   *
   *     // Check the current font
   *     auto ellipsisRun = shaped(run.fFont.refTypeface(), nullptr);
   *     if (ellipsisRun->isResolved()) {
   *         return ellipsisRun;
   *     }
   *
   *     // Check all allowed fonts
   *     std::vector<sk_sp<SkTypeface>> typefaces = fOwner->fontCollection()->findTypefaces(
   *             textStyle.getFontFamilies(), textStyle.getFontStyle(), textStyle.getFontArguments());
   *     for (const auto& typeface : typefaces) {
   *         ellipsisRun = shaped(typeface, nullptr);
   *         if (ellipsisRun->isResolved()) {
   *             return ellipsisRun;
   *         }
   *     }
   *
   *     // Try the fallback
   *     if (fOwner->fontCollection()->fontFallbackEnabled()) {
   *         const char* ch = ellipsis.c_str();
   *       SkUnichar unicode = SkUTF::NextUTF8WithReplacement(&ch,
   *                                                          ellipsis.c_str()
   *                                                              + ellipsis.size());
   *         // We do not expect emojis in ellipsis so if they appeat there
   *         // they will not be resolved with the pretiest color emoji font
   *         auto typeface = fOwner->fontCollection()->defaultFallback(
   *                                             unicode,
   *                                             textStyle.getFontFamilies(),
   *                                             textStyle.getFontStyle(),
   *                                             textStyle.getLocale(),
   *                                             textStyle.getFontArguments());
   *         if (typeface) {
   *             ellipsisRun = shaped(typeface, fOwner->fontCollection()->getFallbackManager());
   *             if (ellipsisRun->isResolved()) {
   *                 return ellipsisRun;
   *             }
   *         }
   *     }
   *     return ellipsisRun;
   * }
   * ```
   */
  private fun justify(maxWidth: SkScalar) {
    TODO("Implement justify")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::justify(SkScalar maxWidth) {
   *     int whitespacePatches = 0;
   *     SkScalar textLen = 0;
   *     SkScalar whitespaceLen = 0;
   *     bool whitespacePatch = false;
   *     // Take leading whitespaces width but do not increment a whitespace patch number
   *     bool leadingWhitespaces = false;
   *     this->iterateThroughClustersInGlyphsOrder(false, false,
   *         [&](const Cluster* cluster, ClusterIndex index, bool ghost) {
   *             if (cluster->isWhitespaceBreak()) {
   *                 if (index == 0) {
   *                     leadingWhitespaces = true;
   *                 } else if (!whitespacePatch && !leadingWhitespaces) {
   *                     // We only count patches BETWEEN words, not before
   *                     ++whitespacePatches;
   *                 }
   *                 whitespacePatch = !leadingWhitespaces;
   *                 whitespaceLen += cluster->width();
   *             } else if (cluster->isIdeographic()) {
   *                 // Whitespace break before and after
   *                 if (!whitespacePatch && index != 0) {
   *                     // We only count patches BETWEEN words, not before
   *                     ++whitespacePatches; // before
   *                 }
   *                 whitespacePatch = true;
   *                 leadingWhitespaces = false;
   *                 ++whitespacePatches;    // after
   *             } else {
   *                 whitespacePatch = false;
   *                 leadingWhitespaces = false;
   *             }
   *             textLen += cluster->width();
   *             return true;
   *         });
   *
   *     if (whitespacePatch) {
   *         // We only count patches BETWEEN words, not after
   *         --whitespacePatches;
   *     }
   *     if (whitespacePatches == 0) {
   *         if (fOwner->paragraphStyle().getTextDirection() == TextDirection::kRtl) {
   *             // Justify -> Right align
   *             fShift = maxWidth - textLen;
   *         }
   *         return;
   *     }
   *
   *     SkScalar step = (maxWidth - textLen + whitespaceLen) / whitespacePatches;
   *     SkScalar shift = 0.0f;
   *     SkScalar prevShift = 0.0f;
   *
   *     // Deal with the ghost spaces
   *     auto ghostShift = maxWidth - this->fAdvance.fX;
   *     // Spread the extra whitespaces
   *     whitespacePatch = false;
   *     // Do not break on leading whitespaces
   *     leadingWhitespaces = false;
   *     this->iterateThroughClustersInGlyphsOrder(false, true, [&](const Cluster* cluster, ClusterIndex index, bool ghost) {
   *
   *         if (ghost) {
   *             if (cluster->run().leftToRight()) {
   *                 this->shiftCluster(cluster, ghostShift, ghostShift);
   *             }
   *             return true;
   *         }
   *
   *         if (cluster->isWhitespaceBreak()) {
   *             if (index == 0) {
   *                 leadingWhitespaces = true;
   *             } else if (!whitespacePatch && !leadingWhitespaces) {
   *                 shift += step;
   *                 whitespacePatch = true;
   *                 --whitespacePatches;
   *             }
   *             shift -= cluster->width();
   *         } else if (cluster->isIdeographic()) {
   *             if (!whitespacePatch && index != 0) {
   *                 shift += step;
   *                --whitespacePatches;
   *             }
   *             whitespacePatch = false;
   *             leadingWhitespaces = false;
   *         } else {
   *             whitespacePatch = false;
   *             leadingWhitespaces = false;
   *         }
   *         this->shiftCluster(cluster, shift, prevShift);
   *         prevShift = shift;
   *         // We skip ideographic whitespaces
   *         if (!cluster->isWhitespaceBreak() && cluster->isIdeographic()) {
   *             shift += step;
   *             whitespacePatch = true;
   *             --whitespacePatches;
   *         }
   *         return true;
   *     });
   *
   *     if (whitespacePatch && whitespacePatches < 0) {
   *         whitespacePatches++;
   *         shift -= step;
   *     }
   *
   *     SkAssertResult(nearlyEqual(shift, maxWidth - textLen));
   *     SkASSERT(whitespacePatches == 0);
   *
   *     this->fWidthWithSpaces += ghostShift;
   *     this->fAdvance.fX = maxWidth;
   * }
   * ```
   */
  private fun buildTextBlob(
    textRange: TextRange,
    style: TextStyle,
    context: ClipContext,
  ) {
    TODO("Implement buildTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::buildTextBlob(TextRange textRange, const TextStyle& style, const ClipContext& context) {
   *     if (context.run->placeholderStyle() != nullptr) {
   *         return;
   *     }
   *
   *     fTextBlobCache.emplace_back();
   *     TextBlobRecord& record = fTextBlobCache.back();
   *
   *     if (style.hasForeground()) {
   *         record.fPaint = style.getForegroundPaintOrID();
   *     } else {
   *         std::get<SkPaint>(record.fPaint).setColor(style.getColor());
   *     }
   *     record.fVisitor_Run = context.run;
   *     record.fVisitor_Pos = context.pos;
   *
   *     // TODO: This is the change for flutter, must be removed later
   *     SkTextBlobBuilder builder;
   *     context.run->copyTo(builder, SkToU32(context.pos), context.size);
   *     record.fClippingNeeded = context.clippingNeeded;
   *     if (context.clippingNeeded) {
   *         record.fClipRect = extendHeight(context).makeOffset(this->offset());
   *     } else {
   *         record.fClipRect = context.clip.makeOffset(this->offset());
   *     }
   *
   *     SkASSERT(nearlyEqual(context.run->baselineShift(), style.getBaselineShift()));
   *     SkScalar correctedBaseline = SkScalarFloorToScalar(this->baseline() + style.getBaselineShift() +  0.5);
   *     record.fBlob = builder.make();
   *     if (record.fBlob != nullptr) {
   *         record.fBounds.joinPossiblyEmptyRect(record.fBlob->bounds());
   *     }
   *
   *     record.fOffset = SkPoint::Make(this->offset().fX + context.fTextShift,
   *                                    this->offset().fY + correctedBaseline);
   * }
   * ```
   */
  private fun paintBackground(
    painter: ParagraphPainter?,
    x: SkScalar,
    y: SkScalar,
    textRange: TextRange,
    style: TextStyle,
    context: ClipContext,
  ) {
    TODO("Implement paintBackground")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::paintBackground(ParagraphPainter* painter,
   *                                SkScalar x,
   *                                SkScalar y,
   *                                TextRange textRange,
   *                                const TextStyle& style,
   *                                const ClipContext& context) const {
   *     if (style.hasBackground()) {
   *         painter->drawRect(context.clip.makeOffset(this->offset() + SkPoint::Make(x, y)),
   *                           style.getBackgroundPaintOrID());
   *     }
   * }
   * ```
   */
  private fun paintShadow(
    painter: ParagraphPainter?,
    x: SkScalar,
    y: SkScalar,
    textRange: TextRange,
    style: TextStyle,
    context: ClipContext,
  ) {
    TODO("Implement paintShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::paintShadow(ParagraphPainter* painter,
   *                            SkScalar x,
   *                            SkScalar y,
   *                            TextRange textRange,
   *                            const TextStyle& style,
   *                            const ClipContext& context) const {
   *     SkScalar correctedBaseline = SkScalarFloorToScalar(this->baseline() + style.getBaselineShift() + 0.5);
   *
   *     for (TextShadow shadow : style.getShadows()) {
   *         if (!shadow.hasShadow()) continue;
   *
   *         SkTextBlobBuilder builder;
   *         context.run->copyTo(builder, context.pos, context.size);
   *
   *         if (context.clippingNeeded) {
   *             painter->save();
   *             SkRect clip = extendHeight(context);
   *             clip.offset(x, y);
   *             clip.offset(this->offset());
   *             painter->clipRect(clip);
   *         }
   *         auto blob = builder.make();
   *         painter->drawTextShadow(blob,
   *             x + this->offset().fX + shadow.fOffset.x() + context.fTextShift,
   *             y + this->offset().fY + shadow.fOffset.y() + correctedBaseline,
   *             shadow.fColor,
   *             SkDoubleToScalar(shadow.fBlurSigma));
   *         if (context.clippingNeeded) {
   *             painter->restore();
   *         }
   *     }
   * }
   * ```
   */
  private fun paintDecorations(
    painter: ParagraphPainter?,
    x: SkScalar,
    y: SkScalar,
    textRange: TextRange,
    style: TextStyle,
    context: ClipContext,
  ) {
    TODO("Implement paintDecorations")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextLine::paintDecorations(ParagraphPainter* painter, SkScalar x, SkScalar y, TextRange textRange, const TextStyle& style, const ClipContext& context) const {
   *     ParagraphPainterAutoRestore ppar(painter);
   *     painter->translate(x + this->offset().fX, y + this->offset().fY + style.getBaselineShift());
   *     Decorations decorations;
   *     SkScalar correctedBaseline = SkScalarFloorToScalar(-this->sizes().rawAscent() + style.getBaselineShift() + 0.5);
   *     decorations.paint(painter, style, context, correctedBaseline);
   * }
   * ```
   */
  private fun shiftCluster(
    cluster: Cluster?,
    shift: SkScalar,
    prevShift: SkScalar,
  ) {
    TODO("Implement shiftCluster")
  }

  public data class ClipContext public constructor(
    public val run: Int?,
    public var pos: ULong,
    public var size: ULong,
    public var fTextShift: Int,
    public var clip: Int,
    public var fExcludedTrailingSpaces: Int,
    public var clippingNeeded: Boolean,
  )

  public data class TextBlobRecord public constructor(
    public var fBlob: Int,
    public var fOffset: Int,
    public var fPaint: Int,
    public var fBounds: Int,
    public var fClippingNeeded: Boolean,
    public var fClipRect: Int,
    public val fVisitorRun: Int?,
    public var fVisitorPos: ULong,
  ) {
    public fun paint(
      painter: ParagraphPainter?,
      x: SkScalar,
      y: SkScalar,
    ) {
      TODO("Implement paint")
    }
  }

  public enum class TextAdjustment {
    GlyphCluster,
    GlyphemeCluster,
    Grapheme,
    GraphemeGluster,
  }
}
