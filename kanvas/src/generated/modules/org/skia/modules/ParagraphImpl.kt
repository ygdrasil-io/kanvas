package org.skia.modules

import GlyphClusterInfo
import GlyphInfo
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.collections.List
import kotlin.u16string
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.core.TArraytrue
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkScalar
import org.skia.math.SkVector
import undefined.ExtendedVisitor

/**
 * C++ original:
 * ```cpp
 * class ParagraphImpl final : public Paragraph {
 *
 * public:
 *
 *     ParagraphImpl(const SkString& text,
 *                   ParagraphStyle style,
 *                   skia_private::TArray<Block, true> blocks,
 *                   skia_private::TArray<Placeholder, true> placeholders,
 *                   sk_sp<FontCollection> fonts,
 *                   sk_sp<SkUnicode> unicode);
 *
 *     ParagraphImpl(const std::u16string& utf16text,
 *                   ParagraphStyle style,
 *                   skia_private::TArray<Block, true> blocks,
 *                   skia_private::TArray<Placeholder, true> placeholders,
 *                   sk_sp<FontCollection> fonts,
 *                   sk_sp<SkUnicode> unicode);
 *
 *     ~ParagraphImpl() override;
 *
 *     void layout(SkScalar width) override;
 *     void paint(SkCanvas* canvas, SkScalar x, SkScalar y) override;
 *     void paint(ParagraphPainter* canvas, SkScalar x, SkScalar y) override;
 *     std::vector<TextBox> getRectsForRange(unsigned start,
 *                                           unsigned end,
 *                                           RectHeightStyle rectHeightStyle,
 *                                           RectWidthStyle rectWidthStyle) override;
 *     std::vector<TextBox> getRectsForPlaceholders() override;
 *     void getLineMetrics(std::vector<LineMetrics>&) override;
 *     PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) override;
 *     SkRange<size_t> getWordBoundary(unsigned offset) override;
 *
 *     bool getApplyRoundingHack() const { return fParagraphStyle.getApplyRoundingHack(); }
 *
 *     size_t lineNumber() override { return fLines.size(); }
 *
 *     TextLine& addLine(SkVector offset, SkVector advance,
 *                       TextRange textExcludingSpaces, TextRange text, TextRange textIncludingNewlines,
 *                       ClusterRange clusters, ClusterRange clustersWithGhosts, SkScalar widthWithSpaces,
 *                       InternalLineMetrics sizes);
 *
 *     SkSpan<const char> text() const { return SkSpan<const char>(fText.c_str(), fText.size()); }
 *     InternalState state() const { return fState; }
 *     SkSpan<Run> runs() { return SkSpan<Run>(fRuns.data(), fRuns.size()); }
 *     SkSpan<Block> styles() {
 *         return SkSpan<Block>(fTextStyles.data(), fTextStyles.size());
 *     }
 *     SkSpan<Placeholder> placeholders() {
 *         return SkSpan<Placeholder>(fPlaceholders.data(), fPlaceholders.size());
 *     }
 *     SkSpan<TextLine> lines() { return SkSpan<TextLine>(fLines.data(), fLines.size()); }
 *     const ParagraphStyle& paragraphStyle() const { return fParagraphStyle; }
 *     SkSpan<Cluster> clusters() { return SkSpan<Cluster>(fClusters.begin(), fClusters.size()); }
 *     sk_sp<FontCollection> fontCollection() const { return fFontCollection; }
 *     void formatLines(SkScalar maxWidth);
 *     void ensureUTF16Mapping();
 *     skia_private::TArray<TextIndex> countSurroundingGraphemes(TextRange textRange) const;
 *     TextIndex findNextGraphemeBoundary(TextIndex utf8) const;
 *     TextIndex findPreviousGraphemeBoundary(TextIndex utf8) const;
 *     TextIndex findNextGlyphClusterBoundary(TextIndex utf8) const;
 *     TextIndex findPreviousGlyphClusterBoundary(TextIndex utf8) const;
 *     size_t getUTF16Index(TextIndex index) const {
 *         return fUTF16IndexForUTF8Index[index];
 *     }
 *
 *     bool strutEnabled() const { return paragraphStyle().getStrutStyle().getStrutEnabled(); }
 *     bool strutForceHeight() const {
 *         return paragraphStyle().getStrutStyle().getForceStrutHeight();
 *     }
 *     bool strutHeightOverride() const {
 *         return paragraphStyle().getStrutStyle().getHeightOverride();
 *     }
 *     InternalLineMetrics strutMetrics() const { return fStrutMetrics; }
 *
 *     SkString getEllipsis() const;
 *
 *     SkSpan<const char> text(TextRange textRange);
 *     SkSpan<Cluster> clusters(ClusterRange clusterRange);
 *     Cluster& cluster(ClusterIndex clusterIndex);
 *     ClusterIndex clusterIndex(TextIndex textIndex) {
 *         auto clusterIndex = this->fClustersIndexFromCodeUnit[textIndex];
 *         SkASSERT(clusterIndex != EMPTY_INDEX);
 *         return clusterIndex;
 *     }
 *     Run& run(RunIndex runIndex) {
 *         SkASSERT(runIndex < SkToSizeT(fRuns.size()));
 *         return fRuns[runIndex];
 *     }
 *
 *     Run& runByCluster(ClusterIndex clusterIndex);
 *     SkSpan<Block> blocks(BlockRange blockRange);
 *     Block& block(BlockIndex blockIndex);
 *     skia_private::TArray<ResolvedFontDescriptor> resolvedFonts() const { return fFontSwitches; }
 *
 *     void markDirty() override {
 *         if (fState > kIndexed) {
 *             fState = kIndexed;
 *         }
 *     }
 *
 *     int32_t unresolvedGlyphs() override;
 *     std::unordered_set<SkUnichar> unresolvedCodepoints() override;
 *     void addUnresolvedCodepoints(TextRange textRange);
 *
 *     void setState(InternalState state);
 *     sk_sp<SkPicture> getPicture() { return fPicture; }
 *
 *     SkScalar widthWithTrailingSpaces() { return fMaxWidthWithTrailingSpaces; }
 *
 *     void resetContext();
 *     void resolveStrut();
 *
 *     bool computeCodeUnitProperties();
 *     void applySpacingAndBuildClusterTable();
 *     void buildClusterTable();
 *     bool shapeTextIntoEndlessLine();
 *     void breakShapedTextIntoLines(SkScalar maxWidth);
 *
 *     void updateTextAlign(TextAlign textAlign) override;
 *     void updateFontSize(size_t from, size_t to, SkScalar fontSize) override;
 *     void updateForegroundPaint(size_t from, size_t to, SkPaint paint) override;
 *     void updateBackgroundPaint(size_t from, size_t to, SkPaint paint) override;
 *
 *     void visit(const Visitor&) override;
 *     void extendedVisit(const ExtendedVisitor&) override;
 *     int getPath(int lineNumber, SkPath* dest) override;
 *     bool containsColorFontOrBitmap(SkTextBlob* textBlob) override;
 *     bool containsEmoji(SkTextBlob* textBlob) override;
 *
 *     int getLineNumberAt(TextIndex codeUnitIndex) const override;
 *     int getLineNumberAtUTF16Offset(size_t codeUnitIndex) override;
 *     bool getLineMetricsAt(int lineNumber, LineMetrics* lineMetrics) const override;
 *     TextRange getActualTextRange(int lineNumber, bool includeSpaces) const override;
 *     bool getGlyphClusterAt(TextIndex codeUnitIndex, GlyphClusterInfo* glyphInfo) override;
 *     bool getClosestGlyphClusterAt(SkScalar dx,
 *                                   SkScalar dy,
 *                                   GlyphClusterInfo* glyphInfo) override;
 *
 *     bool getGlyphInfoAtUTF16Offset(size_t codeUnitIndex, GlyphInfo* graphemeInfo) override;
 *     bool getClosestUTF16GlyphInfoAt(SkScalar dx, SkScalar dy, GlyphInfo* graphemeInfo) override;
 *     SkFont getFontAt(TextIndex codeUnitIndex) const override;
 *     SkFont getFontAtUTF16Offset(size_t codeUnitIndex) override;
 *     std::vector<FontInfo> getFonts() const override;
 *
 *     InternalLineMetrics getEmptyMetrics() const { return fEmptyMetrics; }
 *     InternalLineMetrics getStrutMetrics() const { return fStrutMetrics; }
 *
 *     BlockRange findAllBlocks(TextRange textRange);
 *
 *     void resetShifts() {
 *         for (auto& run : fRuns) {
 *             run.resetJustificationShifts();
 *         }
 *     }
 *
 *     bool codeUnitHasProperty(size_t index, SkUnicode::CodeUnitFlags property) const {
 *         return (fCodeUnitProperties[index] & property) == property;
 *     }
 *
 *     sk_sp<SkUnicode> getUnicode() { return fUnicode; }
 *
 * private:
 *     friend class ParagraphBuilder;
 *     friend class ParagraphCacheKey;
 *     friend class ParagraphCacheValue;
 *     friend class ParagraphCache;
 *
 *     friend class TextWrapper;
 *     friend class OneLineShaper;
 *
 *     void computeEmptyMetrics();
 *
 *     // Input
 *     skia_private::TArray<StyleBlock<SkScalar>> fLetterSpaceStyles;
 *     skia_private::TArray<StyleBlock<SkScalar>> fWordSpaceStyles;
 *     skia_private::TArray<StyleBlock<SkPaint>> fBackgroundStyles;
 *     skia_private::TArray<StyleBlock<SkPaint>> fForegroundStyles;
 *     skia_private::TArray<StyleBlock<std::vector<TextShadow>>> fShadowStyles;
 *     skia_private::TArray<StyleBlock<Decoration>> fDecorationStyles;
 *     skia_private::TArray<Block, true> fTextStyles; // TODO: take out only the font stuff
 *     skia_private::TArray<Placeholder, true> fPlaceholders;
 *     SkString fText;
 *
 *     // Internal structures
 *     InternalState fState;
 *     skia_private::TArray<Run, false> fRuns;         // kShaped
 *     skia_private::TArray<Cluster, true> fClusters;  // kClusterized (cached: text, word spacing, letter spacing, resolved fonts)
 *     skia_private::TArray<SkUnicode::CodeUnitFlags, true> fCodeUnitProperties;
 *     skia_private::TArray<size_t, true> fClustersIndexFromCodeUnit;
 *     std::vector<size_t> fWords;
 *     std::vector<SkUnicode::BidiRegion> fBidiRegions;
 *     // These two arrays are used in measuring methods (getRectsForRange, getGlyphPositionAtCoordinate)
 *     // They are filled lazily whenever they need and cached
 *     skia_private::TArray<TextIndex, true> fUTF8IndexForUTF16Index;
 *     skia_private::TArray<size_t, true> fUTF16IndexForUTF8Index;
 *     SkOnce fillUTF16MappingOnce;
 *     size_t fUnresolvedGlyphs;
 *     std::unordered_set<SkUnichar> fUnresolvedCodepoints;
 *
 *     skia_private::TArray<TextLine, false> fLines;   // kFormatted   (cached: width, max lines, ellipsis, text align)
 *     sk_sp<SkPicture> fPicture;          // kRecorded    (cached: text styles)
 *
 *     skia_private::TArray<ResolvedFontDescriptor> fFontSwitches;
 *
 *     InternalLineMetrics fEmptyMetrics;
 *     InternalLineMetrics fStrutMetrics;
 *
 *     SkScalar fOldWidth;
 *     SkScalar fOldHeight;
 *     SkScalar fMaxWidthWithTrailingSpaces;
 *
 *     sk_sp<SkUnicode> fUnicode;
 *     bool fHasLineBreaks;
 *     bool fHasWhitespacesInside;
 *     TextIndex fTrailingSpaces;
 * }
 * ```
 */
public class ParagraphImpl public constructor(
  text: String,
  style: ParagraphStyle,
  blocks: TArraytrue<Block>,
  placeholders: TArraytrue<Placeholder>,
  fonts: SkSp<FontCollection>,
  unicode: SkSp<SkUnicode>,
) : Paragraph() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StyleBlock<SkScalar>> fLetterSpaceStyles
   * ```
   */
  private var fLetterSpaceStyles: Int = TODO("Initialize fLetterSpaceStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StyleBlock<SkScalar>> fWordSpaceStyles
   * ```
   */
  private var fWordSpaceStyles: Int = TODO("Initialize fWordSpaceStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StyleBlock<SkPaint>> fBackgroundStyles
   * ```
   */
  private var fBackgroundStyles: Int = TODO("Initialize fBackgroundStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StyleBlock<SkPaint>> fForegroundStyles
   * ```
   */
  private var fForegroundStyles: Int = TODO("Initialize fForegroundStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StyleBlock<Decoration>> fDecorationStyles
   * ```
   */
  private var fDecorationStyles: Int = TODO("Initialize fDecorationStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Block, true> fTextStyles
   * ```
   */
  private var fTextStyles: Int = TODO("Initialize fTextStyles")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Placeholder, true> fPlaceholders
   * ```
   */
  private var fPlaceholders: Int = TODO("Initialize fPlaceholders")

  /**
   * C++ original:
   * ```cpp
   * SkString fText
   * ```
   */
  private var fText: Int = TODO("Initialize fText")

  /**
   * C++ original:
   * ```cpp
   * InternalState fState
   * ```
   */
  private var fState: InternalState = TODO("Initialize fState")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Run, false> fRuns
   * ```
   */
  private var fRuns: Int = TODO("Initialize fRuns")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Cluster, true> fClusters
   * ```
   */
  private var fClusters: Int = TODO("Initialize fClusters")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkUnicode::CodeUnitFlags, true> fCodeUnitProperties
   * ```
   */
  private var fCodeUnitProperties: Int = TODO("Initialize fCodeUnitProperties")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<size_t, true> fClustersIndexFromCodeUnit
   * ```
   */
  private var fClustersIndexFromCodeUnit: Int = TODO("Initialize fClustersIndexFromCodeUnit")

  /**
   * C++ original:
   * ```cpp
   * std::vector<size_t> fWords
   * ```
   */
  private var fWords: Int = TODO("Initialize fWords")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkUnicode::BidiRegion> fBidiRegions
   * ```
   */
  private var fBidiRegions: Int = TODO("Initialize fBidiRegions")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextIndex, true> fUTF8IndexForUTF16Index
   * ```
   */
  private var fUTF8IndexForUTF16Index: Int = TODO("Initialize fUTF8IndexForUTF16Index")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<size_t, true> fUTF16IndexForUTF8Index
   * ```
   */
  private var fUTF16IndexForUTF8Index: Int = TODO("Initialize fUTF16IndexForUTF8Index")

  /**
   * C++ original:
   * ```cpp
   * SkOnce fillUTF16MappingOnce
   * ```
   */
  private var fillUTF16MappingOnce: Int = TODO("Initialize fillUTF16MappingOnce")

  /**
   * C++ original:
   * ```cpp
   * size_t fUnresolvedGlyphs
   * ```
   */
  private var fUnresolvedGlyphs: Int = TODO("Initialize fUnresolvedGlyphs")

  /**
   * C++ original:
   * ```cpp
   * std::unordered_set<SkUnichar> fUnresolvedCodepoints
   * ```
   */
  private var fUnresolvedCodepoints: Int = TODO("Initialize fUnresolvedCodepoints")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextLine, false> fLines
   * ```
   */
  private var fLines: Int = TODO("Initialize fLines")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: Int = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<ResolvedFontDescriptor> fFontSwitches
   * ```
   */
  private var fFontSwitches: Int = TODO("Initialize fFontSwitches")

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics fEmptyMetrics
   * ```
   */
  private var fEmptyMetrics: Int = TODO("Initialize fEmptyMetrics")

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics fStrutMetrics
   * ```
   */
  private var fStrutMetrics: Int = TODO("Initialize fStrutMetrics")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fOldWidth
   * ```
   */
  private var fOldWidth: Int = TODO("Initialize fOldWidth")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fOldHeight
   * ```
   */
  private var fOldHeight: Int = TODO("Initialize fOldHeight")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fMaxWidthWithTrailingSpaces
   * ```
   */
  private var fMaxWidthWithTrailingSpaces: Int = TODO("Initialize fMaxWidthWithTrailingSpaces")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkUnicode> fUnicode
   * ```
   */
  private var fUnicode: Int = TODO("Initialize fUnicode")

  /**
   * C++ original:
   * ```cpp
   * bool fHasLineBreaks
   * ```
   */
  private var fHasLineBreaks: Boolean = TODO("Initialize fHasLineBreaks")

  /**
   * C++ original:
   * ```cpp
   * bool fHasWhitespacesInside
   * ```
   */
  private var fHasWhitespacesInside: Boolean = TODO("Initialize fHasWhitespacesInside")

  /**
   * C++ original:
   * ```cpp
   * TextIndex fTrailingSpaces
   * ```
   */
  private var fTrailingSpaces: Int = TODO("Initialize fTrailingSpaces")

  /**
   * C++ original:
   * ```cpp
   * ParagraphImpl(const SkString& text,
   *                   ParagraphStyle style,
   *                   skia_private::TArray<Block, true> blocks,
   *                   skia_private::TArray<Placeholder, true> placeholders,
   *                   sk_sp<FontCollection> fonts,
   *                   sk_sp<SkUnicode> unicode)
   * ```
   */
  public constructor(
    utf16text: u16string,
    style: ParagraphStyle,
    blocks: TArraytrue<Block>,
    placeholders: TArraytrue<Placeholder>,
    fonts: SkSp<FontCollection>,
    unicode: SkSp<SkUnicode>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::layout(SkScalar rawWidth) {
   *     // TODO: This rounding is done to match Flutter tests. Must be removed...
   *     auto floorWidth = rawWidth;
   *     if (getApplyRoundingHack()) {
   *         floorWidth = SkScalarFloorToScalar(floorWidth);
   *     }
   *
   *     if ((!SkIsFinite(rawWidth) || fLongestLine <= floorWidth) &&
   *         fState >= kLineBroken &&
   *          fLines.size() == 1 && fLines.front().ellipsis() == nullptr) {
   *         // Most common case: one line of text (and one line is never justified, so no cluster shifts)
   *         // We cannot mark it as kLineBroken because the new width can be bigger than the old width
   *         fWidth = floorWidth;
   *         fState = kShaped;
   *     } else if (fState >= kLineBroken && fOldWidth != floorWidth) {
   *         // We can use the results from SkShaper but have to do EVERYTHING ELSE again
   *         fState = kShaped;
   *     } else {
   *         // Nothing changed case: we can reuse the data from the last layout
   *     }
   *
   *     if (fState < kShaped) {
   *         // Check if we have the text in the cache and don't need to shape it again
   *         if (!fFontCollection->getParagraphCache()->findParagraph(this)) {
   *             if (fState < kIndexed) {
   *                 // This only happens once at the first layout; the text is immutable
   *                 // and there is no reason to repeat it
   *                 if (this->computeCodeUnitProperties()) {
   *                     fState = kIndexed;
   *                 }
   *             }
   *             this->fRuns.clear();
   *             this->fClusters.clear();
   *             this->fClustersIndexFromCodeUnit.clear();
   *             this->fClustersIndexFromCodeUnit.push_back_n(fText.size() + 1, EMPTY_INDEX);
   *             if (!this->shapeTextIntoEndlessLine()) {
   *                 this->resetContext();
   *                 // TODO: merge the two next calls - they always come together
   *                 this->resolveStrut();
   *                 this->computeEmptyMetrics();
   *                 this->fLines.clear();
   *
   *                 // Set the important values that are not zero
   *                 fWidth = floorWidth;
   *                 fHeight = fEmptyMetrics.height();
   *                 if (fParagraphStyle.getStrutStyle().getStrutEnabled() &&
   *                     fParagraphStyle.getStrutStyle().getForceStrutHeight()) {
   *                     fHeight = fStrutMetrics.height();
   *                 }
   *                 fAlphabeticBaseline = fEmptyMetrics.alphabeticBaseline();
   *                 fIdeographicBaseline = fEmptyMetrics.ideographicBaseline();
   *                 fLongestLine = FLT_MIN - FLT_MAX;  // That is what flutter has
   *                 fMinIntrinsicWidth = 0;
   *                 fMaxIntrinsicWidth = 0;
   *                 this->fOldWidth = floorWidth;
   *                 this->fOldHeight = this->fHeight;
   *
   *                 return;
   *             } else {
   *                 // Add the paragraph to the cache
   *                 fFontCollection->getParagraphCache()->updateParagraph(this);
   *             }
   *         }
   *         fState = kShaped;
   *     }
   *
   *     if (fState == kShaped) {
   *         this->resetContext();
   *         this->resolveStrut();
   *         this->computeEmptyMetrics();
   *         this->fLines.clear();
   *         this->breakShapedTextIntoLines(floorWidth);
   *         fState = kLineBroken;
   *     }
   *
   *     if (fState == kLineBroken) {
   *         // Build the picture lazily not until we actually have to paint (or never)
   *         this->resetShifts();
   *         this->formatLines(fWidth);
   *         fState = kFormatted;
   *     }
   *
   *     this->fOldWidth = floorWidth;
   *     this->fOldHeight = this->fHeight;
   *
   *     if (getApplyRoundingHack()) {
   *         // TODO: This rounding is done to match Flutter tests. Must be removed...
   *         fMinIntrinsicWidth = littleRound(fMinIntrinsicWidth);
   *         fMaxIntrinsicWidth = littleRound(fMaxIntrinsicWidth);
   *     }
   *
   *     // TODO: This is strictly Flutter thing. Must be factored out into some flutter code
   *     if (fParagraphStyle.getMaxLines() == 1 ||
   *         (fParagraphStyle.unlimited_lines() && fParagraphStyle.ellipsized())) {
   *         fMinIntrinsicWidth = fMaxIntrinsicWidth;
   *     }
   *
   *     // TODO: Since min and max are calculated differently it's possible to get a rounding error
   *     //  that would make min > max. Sort it out later, make it the same for now
   *     if (fMaxIntrinsicWidth < fMinIntrinsicWidth) {
   *         fMaxIntrinsicWidth = fMinIntrinsicWidth;
   *     }
   *
   *     //SkDebugf("layout('%s', %f): %f %f\n", fText.c_str(), rawWidth, fMinIntrinsicWidth, fMaxIntrinsicWidth);
   * }
   * ```
   */
  public override fun layout(width: SkScalar) {
    TODO("Implement layout")
  }

  /**
   * C++ original:
   * ```cpp
   * void paint(SkCanvas* canvas, SkScalar x, SkScalar y) override
   * ```
   */
  public override fun paint(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * void paint(ParagraphPainter* canvas, SkScalar x, SkScalar y) override
   * ```
   */
  public override fun paint(
    canvas: ParagraphPainter?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<TextBox> ParagraphImpl::getRectsForRange(unsigned start,
   *                                                      unsigned end,
   *                                                      RectHeightStyle rectHeightStyle,
   *                                                      RectWidthStyle rectWidthStyle) {
   *     std::vector<TextBox> results;
   *     if (fText.isEmpty()) {
   *         if (start == 0 && end > 0) {
   *             // On account of implied "\n" that is always at the end of the text
   *             //SkDebugf("getRectsForRange(%d, %d): %f\n", start, end, fHeight);
   *             results.emplace_back(SkRect::MakeXYWH(0, 0, 0, fHeight), fParagraphStyle.getTextDirection());
   *         }
   *         return results;
   *     }
   *
   *     this->ensureUTF16Mapping();
   *
   *     if (start >= end || start > SkToSizeT(fUTF8IndexForUTF16Index.size()) || end == 0) {
   *         return results;
   *     }
   *
   *     // Adjust the text to grapheme edges
   *     // Apparently, text editor CAN move inside graphemes but CANNOT select a part of it.
   *     // I don't know why - the solution I have here returns an empty box for every query that
   *     // does not contain an end of a grapheme.
   *     // Once a cursor is inside a complex grapheme I can press backspace and cause trouble.
   *     // To avoid any problems, I will not allow any selection of a part of a grapheme.
   *     // One flutter test fails because of it but the editing experience is correct
   *     // (although you have to press the cursor many times before it moves to the next grapheme).
   *     TextRange text(fText.size(), fText.size());
   *     // TODO: This is probably a temp change that makes SkParagraph work as TxtLib
   *     //  (so we can compare the results). We now include in the selection box only the graphemes
   *     //  that belongs to the given [start:end) range entirely (not the ones that intersect with it)
   *     if (start < SkToSizeT(fUTF8IndexForUTF16Index.size())) {
   *         auto utf8 = fUTF8IndexForUTF16Index[start];
   *         // If start points to a trailing surrogate, skip it
   *         if (start > 0 && fUTF8IndexForUTF16Index[start - 1] == utf8) {
   *             utf8 = fUTF8IndexForUTF16Index[start + 1];
   *         }
   *         text.start = this->findNextGraphemeBoundary(utf8);
   *     }
   *     if (end < SkToSizeT(fUTF8IndexForUTF16Index.size())) {
   *         auto utf8 = this->findPreviousGraphemeBoundary(fUTF8IndexForUTF16Index[end]);
   *         text.end = utf8;
   *     }
   *     //SkDebugf("getRectsForRange(%d,%d) -> (%d:%d)\n", start, end, text.start, text.end);
   *     for (auto& line : fLines) {
   *         auto lineText = line.textWithNewlines();
   *         auto intersect = lineText * text;
   *         if (intersect.empty() && lineText.start != text.start) {
   *             continue;
   *         }
   *
   *         line.getRectsForRange(intersect, rectHeightStyle, rectWidthStyle, results);
   *     }
   * /*
   *     SkDebugf("getRectsForRange(%d, %d)\n", start, end);
   *     for (auto& r : results) {
   *         r.rect.fLeft = littleRound(r.rect.fLeft);
   *         r.rect.fRight = littleRound(r.rect.fRight);
   *         r.rect.fTop = littleRound(r.rect.fTop);
   *         r.rect.fBottom = littleRound(r.rect.fBottom);
   *         SkDebugf("[%f:%f * %f:%f]\n", r.rect.fLeft, r.rect.fRight, r.rect.fTop, r.rect.fBottom);
   *     }
   * */
   *     return results;
   * }
   * ```
   */
  public override fun getRectsForRange(
    start: UInt,
    end: UInt,
    rectHeightStyle: RectHeightStyle,
    rectWidthStyle: RectWidthStyle,
  ): Int {
    TODO("Implement getRectsForRange")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<TextBox> ParagraphImpl::getRectsForPlaceholders() {
   *   std::vector<TextBox> boxes;
   *   if (fText.isEmpty()) {
   *        return boxes;
   *   }
   *   if (fPlaceholders.size() == 1) {
   *        // We always have one fake placeholder
   *        return boxes;
   *   }
   *   for (auto& line : fLines) {
   *       line.getRectsForPlaceholders(boxes);
   *   }
   *   /*
   *   SkDebugf("getRectsForPlaceholders('%s'): %d\n", fText.c_str(), boxes.size());
   *   for (auto& r : boxes) {
   *       r.rect.fLeft = littleRound(r.rect.fLeft);
   *       r.rect.fRight = littleRound(r.rect.fRight);
   *       r.rect.fTop = littleRound(r.rect.fTop);
   *       r.rect.fBottom = littleRound(r.rect.fBottom);
   *       SkDebugf("[%f:%f * %f:%f] %s\n", r.rect.fLeft, r.rect.fRight, r.rect.fTop, r.rect.fBottom,
   *                (r.direction == TextDirection::kLtr ? "left" : "right"));
   *   }
   *   */
   *   return boxes;
   * }
   * ```
   */
  public override fun getRectsForPlaceholders(): Int {
    TODO("Implement getRectsForPlaceholders")
  }

  /**
   * C++ original:
   * ```cpp
   * void getLineMetrics(std::vector<LineMetrics>&) override
   * ```
   */
  public override fun getLineMetrics(param0: List<LineMetrics>) {
    TODO("Implement getLineMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * PositionWithAffinity ParagraphImpl::getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) {
   *
   *     if (fText.isEmpty()) {
   *         return {0, Affinity::kDownstream};
   *     }
   *
   *     this->ensureUTF16Mapping();
   *
   *     for (auto& line : fLines) {
   *         // Let's figure out if we can stop looking
   *         auto offsetY = line.offset().fY;
   *         if (dy >= offsetY + line.height() && &line != &fLines.back()) {
   *             // This line is not good enough
   *             continue;
   *         }
   *
   *         // This is so far the the line vertically closest to our coordinates
   *         // (or the first one, or the only one - all the same)
   *
   *         auto result = line.getGlyphPositionAtCoordinate(dx);
   *         //SkDebugf("getGlyphPositionAtCoordinate(%f, %f): %d %s\n", dx, dy, result.position,
   *         //   result.affinity == Affinity::kUpstream ? "up" : "down");
   *         return result;
   *     }
   *
   *     return {0, Affinity::kDownstream};
   * }
   * ```
   */
  public override fun getGlyphPositionAtCoordinate(dx: SkScalar, dy: SkScalar): Int {
    TODO("Implement getGlyphPositionAtCoordinate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRange<size_t> ParagraphImpl::getWordBoundary(unsigned offset) {
   *
   *     if (fWords.empty()) {
   *         if (!fUnicode->getWords(fText.c_str(), fText.size(), nullptr, &fWords)) {
   *             return {0, 0 };
   *         }
   *     }
   *
   *     int32_t start = 0;
   *     int32_t end = 0;
   *     for (size_t i = 0; i < fWords.size(); ++i) {
   *         auto word = fWords[i];
   *         if (word <= offset) {
   *             start = word;
   *             end = word;
   *         } else if (word > offset) {
   *             end = word;
   *             break;
   *         }
   *     }
   *
   *     //SkDebugf("getWordBoundary(%d): %d - %d\n", offset, start, end);
   *     return { SkToU32(start), SkToU32(end) };
   * }
   * ```
   */
  public override fun getWordBoundary(offset: UInt): Int {
    TODO("Implement getWordBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getApplyRoundingHack() const { return fParagraphStyle.getApplyRoundingHack(); }
   * ```
   */
  public fun getApplyRoundingHack(): Boolean {
    TODO("Implement getApplyRoundingHack")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t lineNumber() override { return fLines.size(); }
   * ```
   */
  public override fun lineNumber(): Int {
    TODO("Implement lineNumber")
  }

  /**
   * C++ original:
   * ```cpp
   * TextLine& ParagraphImpl::addLine(SkVector offset,
   *                                  SkVector advance,
   *                                  TextRange textExcludingSpaces,
   *                                  TextRange text,
   *                                  TextRange textIncludingNewLines,
   *                                  ClusterRange clusters,
   *                                  ClusterRange clustersWithGhosts,
   *                                  SkScalar widthWithSpaces,
   *                                  InternalLineMetrics sizes) {
   *     // Define a list of styles that covers the line
   *     auto blocks = findAllBlocks(textExcludingSpaces);
   *     return fLines.emplace_back(this, offset, advance, blocks,
   *                                textExcludingSpaces, text, textIncludingNewLines,
   *                                clusters, clustersWithGhosts, widthWithSpaces, sizes);
   * }
   * ```
   */
  public fun addLine(
    offset: SkVector,
    advance: SkVector,
    textExcludingSpaces: TextRange,
    text: TextRange,
    textIncludingNewlines: TextRange,
    clusters: ClusterRange,
    clustersWithGhosts: ClusterRange,
    widthWithSpaces: SkScalar,
    sizes: InternalLineMetrics,
  ): TextLine {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const char> text() const { return SkSpan<const char>(fText.c_str(), fText.size()); }
   * ```
   */
  public fun text(): Int {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalState state() const { return fState; }
   * ```
   */
  public fun state(): InternalState {
    TODO("Implement state")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Run> runs() { return SkSpan<Run>(fRuns.data(), fRuns.size()); }
   * ```
   */
  public fun runs(): Int {
    TODO("Implement runs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Block> styles() {
   *         return SkSpan<Block>(fTextStyles.data(), fTextStyles.size());
   *     }
   * ```
   */
  public fun styles(): Int {
    TODO("Implement styles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Placeholder> placeholders() {
   *         return SkSpan<Placeholder>(fPlaceholders.data(), fPlaceholders.size());
   *     }
   * ```
   */
  public fun placeholders(): Int {
    TODO("Implement placeholders")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<TextLine> lines() { return SkSpan<TextLine>(fLines.data(), fLines.size()); }
   * ```
   */
  public fun lines(): Int {
    TODO("Implement lines")
  }

  /**
   * C++ original:
   * ```cpp
   * const ParagraphStyle& paragraphStyle() const { return fParagraphStyle; }
   * ```
   */
  public fun paragraphStyle(): Int {
    TODO("Implement paragraphStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Cluster> clusters() { return SkSpan<Cluster>(fClusters.begin(), fClusters.size()); }
   * ```
   */
  public fun clusters(): Int {
    TODO("Implement clusters")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<FontCollection> fontCollection() const { return fFontCollection; }
   * ```
   */
  public fun fontCollection(): Int {
    TODO("Implement fontCollection")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::formatLines(SkScalar maxWidth) {
   *     auto effectiveAlign = fParagraphStyle.effective_align();
   *     const bool isLeftAligned = effectiveAlign == TextAlign::kLeft
   *         || (effectiveAlign == TextAlign::kJustify && fParagraphStyle.getTextDirection() == TextDirection::kLtr);
   *
   *     if (!SkIsFinite(maxWidth) && !isLeftAligned) {
   *         // Special case: clean all text in case of maxWidth == INF & align != left
   *         // We had to go through shaping though because we need all the measurement numbers
   *         fLines.clear();
   *         return;
   *     }
   *
   *     for (auto& line : fLines) {
   *         line.format(effectiveAlign, maxWidth);
   *     }
   * }
   * ```
   */
  public fun formatLines(maxWidth: SkScalar) {
    TODO("Implement formatLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::ensureUTF16Mapping() {
   *     fillUTF16MappingOnce([&] {
   *         SkUnicode::extractUtfConversionMapping(
   *                 this->text(),
   *                 [&](size_t index) { fUTF8IndexForUTF16Index.emplace_back(index); },
   *                 [&](size_t index) { fUTF16IndexForUTF8Index.emplace_back(index); });
   *     });
   * }
   * ```
   */
  public fun ensureUTF16Mapping() {
    TODO("Implement ensureUTF16Mapping")
  }

  /**
   * C++ original:
   * ```cpp
   * TArray<TextIndex> ParagraphImpl::countSurroundingGraphemes(TextRange textRange) const {
   *     textRange = textRange.intersection({0, fText.size()});
   *     TArray<TextIndex> graphemes;
   *     if ((fCodeUnitProperties[textRange.start] & SkUnicode::CodeUnitFlags::kGraphemeStart) == 0) {
   *         // Count the previous partial grapheme
   *         graphemes.emplace_back(textRange.start);
   *     }
   *     for (auto index = textRange.start; index < textRange.end; ++index) {
   *         if ((fCodeUnitProperties[index] & SkUnicode::CodeUnitFlags::kGraphemeStart) != 0) {
   *             graphemes.emplace_back(index);
   *         }
   *     }
   *     return graphemes;
   * }
   * ```
   */
  public fun countSurroundingGraphemes(textRange: TextRange): Int {
    TODO("Implement countSurroundingGraphemes")
  }

  /**
   * C++ original:
   * ```cpp
   * TextIndex ParagraphImpl::findNextGraphemeBoundary(TextIndex utf8) const {
   *     while (utf8 < fText.size() &&
   *           (fCodeUnitProperties[utf8] & SkUnicode::CodeUnitFlags::kGraphemeStart) == 0) {
   *         ++utf8;
   *     }
   *     return utf8;
   * }
   * ```
   */
  public fun findNextGraphemeBoundary(utf8: TextIndex): Int {
    TODO("Implement findNextGraphemeBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * TextIndex ParagraphImpl::findPreviousGraphemeBoundary(TextIndex utf8) const {
   *     while (utf8 > 0 &&
   *           (fCodeUnitProperties[utf8] & SkUnicode::CodeUnitFlags::kGraphemeStart) == 0) {
   *         --utf8;
   *     }
   *     return utf8;
   * }
   * ```
   */
  public fun findPreviousGraphemeBoundary(utf8: TextIndex): Int {
    TODO("Implement findPreviousGraphemeBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * TextIndex ParagraphImpl::findNextGlyphClusterBoundary(TextIndex utf8) const {
   *     while (utf8 < fText.size() &&
   *           (fCodeUnitProperties[utf8] & SkUnicode::CodeUnitFlags::kGlyphClusterStart) == 0) {
   *         ++utf8;
   *     }
   *     return utf8;
   * }
   * ```
   */
  public fun findNextGlyphClusterBoundary(utf8: TextIndex): Int {
    TODO("Implement findNextGlyphClusterBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * TextIndex ParagraphImpl::findPreviousGlyphClusterBoundary(TextIndex utf8) const {
   *     while (utf8 > 0 &&
   *           (fCodeUnitProperties[utf8] & SkUnicode::CodeUnitFlags::kGlyphClusterStart) == 0) {
   *         --utf8;
   *     }
   *     return utf8;
   * }
   * ```
   */
  public fun findPreviousGlyphClusterBoundary(utf8: TextIndex): Int {
    TODO("Implement findPreviousGlyphClusterBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getUTF16Index(TextIndex index) const {
   *         return fUTF16IndexForUTF8Index[index];
   *     }
   * ```
   */
  public fun getUTF16Index(index: TextIndex): Int {
    TODO("Implement getUTF16Index")
  }

  /**
   * C++ original:
   * ```cpp
   * bool strutEnabled() const { return paragraphStyle().getStrutStyle().getStrutEnabled(); }
   * ```
   */
  public fun strutEnabled(): Boolean {
    TODO("Implement strutEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool strutForceHeight() const {
   *         return paragraphStyle().getStrutStyle().getForceStrutHeight();
   *     }
   * ```
   */
  public fun strutForceHeight(): Boolean {
    TODO("Implement strutForceHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * bool strutHeightOverride() const {
   *         return paragraphStyle().getStrutStyle().getHeightOverride();
   *     }
   * ```
   */
  public fun strutHeightOverride(): Boolean {
    TODO("Implement strutHeightOverride")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics strutMetrics() const { return fStrutMetrics; }
   * ```
   */
  public fun strutMetrics(): Int {
    TODO("Implement strutMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString ParagraphImpl::getEllipsis() const {
   *
   *     auto ellipsis8 = fParagraphStyle.getEllipsis();
   *     auto ellipsis16 = fParagraphStyle.getEllipsisUtf16();
   *     if (!ellipsis8.isEmpty()) {
   *         return ellipsis8;
   *     } else {
   *         return SkUnicode::convertUtf16ToUtf8(fParagraphStyle.getEllipsisUtf16());
   *     }
   * }
   * ```
   */
  public fun getEllipsis(): Int {
    TODO("Implement getEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const char> ParagraphImpl::text(TextRange textRange) {
   *     SkASSERT(textRange.start <= fText.size() && textRange.end <= fText.size());
   *     auto start = fText.c_str() + textRange.start;
   *     return SkSpan<const char>(start, textRange.width());
   * }
   * ```
   */
  public fun text(textRange: TextRange): Int {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Cluster> ParagraphImpl::clusters(ClusterRange clusterRange) {
   *     SkASSERT(clusterRange.start < SkToSizeT(fClusters.size()) &&
   *              clusterRange.end <= SkToSizeT(fClusters.size()));
   *     return SkSpan<Cluster>(&fClusters[clusterRange.start], clusterRange.width());
   * }
   * ```
   */
  public fun clusters(clusterRange: ClusterRange): Int {
    TODO("Implement clusters")
  }

  /**
   * C++ original:
   * ```cpp
   * Cluster& ParagraphImpl::cluster(ClusterIndex clusterIndex) {
   *     SkASSERT(clusterIndex < SkToSizeT(fClusters.size()));
   *     return fClusters[clusterIndex];
   * }
   * ```
   */
  public fun cluster(clusterIndex: ClusterIndex): Int {
    TODO("Implement cluster")
  }

  /**
   * C++ original:
   * ```cpp
   * ClusterIndex clusterIndex(TextIndex textIndex) {
   *         auto clusterIndex = this->fClustersIndexFromCodeUnit[textIndex];
   *         SkASSERT(clusterIndex != EMPTY_INDEX);
   *         return clusterIndex;
   *     }
   * ```
   */
  public fun clusterIndex(textIndex: TextIndex): Int {
    TODO("Implement clusterIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * Run& run(RunIndex runIndex) {
   *         SkASSERT(runIndex < SkToSizeT(fRuns.size()));
   *         return fRuns[runIndex];
   *     }
   * ```
   */
  public fun run(runIndex: RunIndex): Int {
    TODO("Implement run")
  }

  /**
   * C++ original:
   * ```cpp
   * Run& ParagraphImpl::runByCluster(ClusterIndex clusterIndex) {
   *     auto start = cluster(clusterIndex);
   *     return this->run(start.fRunIndex);
   * }
   * ```
   */
  public fun runByCluster(clusterIndex: ClusterIndex): Int {
    TODO("Implement runByCluster")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Block> ParagraphImpl::blocks(BlockRange blockRange) {
   *     SkASSERT(blockRange.start < SkToSizeT(fTextStyles.size()) &&
   *              blockRange.end <= SkToSizeT(fTextStyles.size()));
   *     return SkSpan<Block>(&fTextStyles[blockRange.start], blockRange.width());
   * }
   * ```
   */
  public fun blocks(blockRange: BlockRange): Int {
    TODO("Implement blocks")
  }

  /**
   * C++ original:
   * ```cpp
   * Block& ParagraphImpl::block(BlockIndex blockIndex) {
   *     SkASSERT(blockIndex < SkToSizeT(fTextStyles.size()));
   *     return fTextStyles[blockIndex];
   * }
   * ```
   */
  public fun block(blockIndex: BlockIndex): Int {
    TODO("Implement block")
  }

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<ResolvedFontDescriptor> resolvedFonts() const { return fFontSwitches; }
   * ```
   */
  public fun resolvedFonts(): Int {
    TODO("Implement resolvedFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * void markDirty() override {
   *         if (fState > kIndexed) {
   *             fState = kIndexed;
   *         }
   *     }
   * ```
   */
  public override fun markDirty() {
    TODO("Implement markDirty")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t ParagraphImpl::unresolvedGlyphs() {
   *     if (fState < kShaped) {
   *         return -1;
   *     }
   *
   *     return fUnresolvedGlyphs;
   * }
   * ```
   */
  public override fun unresolvedGlyphs(): Int {
    TODO("Implement unresolvedGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unordered_set<SkUnichar> ParagraphImpl::unresolvedCodepoints() {
   *     return fUnresolvedCodepoints;
   * }
   * ```
   */
  public override fun unresolvedCodepoints(): Int {
    TODO("Implement unresolvedCodepoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::addUnresolvedCodepoints(TextRange textRange) {
   *     fUnicode->forEachCodepoint(
   *         &fText[textRange.start], textRange.width(),
   *         [&](SkUnichar unichar, int32_t start, int32_t end, int32_t count) {
   *             fUnresolvedCodepoints.emplace(unichar);
   *         }
   *     );
   * }
   * ```
   */
  public fun addUnresolvedCodepoints(textRange: TextRange) {
    TODO("Implement addUnresolvedCodepoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::setState(InternalState state) {
   *     if (fState <= state) {
   *         fState = state;
   *         return;
   *     }
   *
   *     fState = state;
   *     switch (fState) {
   *         case kUnknown:
   *             SkASSERT(false);
   *             /*
   *             // The text is immutable and so are all the text indexing properties
   *             // taken from SkUnicode
   *             fCodeUnitProperties.reset();
   *             fWords.clear();
   *             fBidiRegions.clear();
   *             fUTF8IndexForUTF16Index.reset();
   *             fUTF16IndexForUTF8Index.reset();
   *             */
   *             [[fallthrough]];
   *
   *         case kIndexed:
   *             fRuns.clear();
   *             fClusters.clear();
   *             [[fallthrough]];
   *
   *         case kShaped:
   *             fLines.clear();
   *             [[fallthrough]];
   *
   *         case kLineBroken:
   *             fPicture = nullptr;
   *             [[fallthrough]];
   *
   *         default:
   *             break;
   *     }
   * }
   * ```
   */
  public fun setState(state: InternalState) {
    TODO("Implement setState")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> getPicture() { return fPicture; }
   * ```
   */
  public fun getPicture(): Int {
    TODO("Implement getPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar widthWithTrailingSpaces() { return fMaxWidthWithTrailingSpaces; }
   * ```
   */
  public fun widthWithTrailingSpaces(): Int {
    TODO("Implement widthWithTrailingSpaces")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::resetContext() {
   *     fAlphabeticBaseline = 0;
   *     fHeight = 0;
   *     fWidth = 0;
   *     fIdeographicBaseline = 0;
   *     fMaxIntrinsicWidth = 0;
   *     fMinIntrinsicWidth = 0;
   *     fLongestLine = 0;
   *     fMaxWidthWithTrailingSpaces = 0;
   *     fExceededMaxLines = false;
   * }
   * ```
   */
  public fun resetContext() {
    TODO("Implement resetContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::resolveStrut() {
   *     auto strutStyle = this->paragraphStyle().getStrutStyle();
   *     if (!strutStyle.getStrutEnabled() || strutStyle.getFontSize() < 0) {
   *         return;
   *     }
   *
   *     std::vector<sk_sp<SkTypeface>> typefaces = fFontCollection->findTypefaces(strutStyle.getFontFamilies(), strutStyle.getFontStyle(), std::nullopt);
   *     if (typefaces.empty()) {
   *         SkDEBUGF("Could not resolve strut font\n");
   *         return;
   *     }
   *
   *     SkFont font(typefaces.front(), strutStyle.getFontSize());
   *     SkFontMetrics metrics;
   *     font.getMetrics(&metrics);
   *     const SkScalar strutLeading = strutStyle.getLeading() < 0 ? 0 : strutStyle.getLeading() * strutStyle.getFontSize();
   *
   *     if (strutStyle.getHeightOverride()) {
   *         SkScalar strutAscent = 0.0f;
   *         SkScalar strutDescent = 0.0f;
   *         // The half leading flag doesn't take effect unless there's height override.
   *         if (strutStyle.getHalfLeading()) {
   *             const auto occupiedHeight = metrics.fDescent - metrics.fAscent;
   *             auto flexibleHeight = strutStyle.getHeight() * strutStyle.getFontSize() - occupiedHeight;
   *             // Distribute the flexible height evenly over and under.
   *             flexibleHeight /= 2;
   *             strutAscent = metrics.fAscent - flexibleHeight;
   *             strutDescent = metrics.fDescent + flexibleHeight;
   *         } else {
   *             const SkScalar strutMetricsHeight = metrics.fDescent - metrics.fAscent + metrics.fLeading;
   *             const auto strutHeightMultiplier = strutMetricsHeight == 0
   *               ? strutStyle.getHeight()
   *               : strutStyle.getHeight() * strutStyle.getFontSize() / strutMetricsHeight;
   *             strutAscent = metrics.fAscent * strutHeightMultiplier;
   *             strutDescent = metrics.fDescent * strutHeightMultiplier;
   *         }
   *         fStrutMetrics = InternalLineMetrics(
   *             strutAscent,
   *             strutDescent,
   *             strutLeading,
   *             metrics.fAscent, metrics.fDescent, metrics.fLeading);
   *     } else {
   *         fStrutMetrics = InternalLineMetrics(
   *                 metrics.fAscent,
   *                 metrics.fDescent,
   *                 strutLeading);
   *     }
   *     fStrutMetrics.setForceStrut(this->paragraphStyle().getStrutStyle().getForceStrutHeight());
   * }
   * ```
   */
  public fun resolveStrut() {
    TODO("Implement resolveStrut")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::computeCodeUnitProperties() {
   *
   *     if (nullptr == fUnicode) {
   *         return false;
   *     }
   *
   *     // Get bidi regions
   *     auto textDirection = fParagraphStyle.getTextDirection() == TextDirection::kLtr
   *                               ? SkUnicode::TextDirection::kLTR
   *                               : SkUnicode::TextDirection::kRTL;
   *     if (!fUnicode->getBidiRegions(fText.c_str(), fText.size(), textDirection, &fBidiRegions)) {
   *         return false;
   *     }
   *
   *     // Collect all spaces and some extra information
   *     // (and also substitute \t with a space while we are at it)
   *     if (!fUnicode->computeCodeUnitFlags(&fText[0],
   *                                         fText.size(),
   *                                         this->paragraphStyle().getReplaceTabCharacters(),
   *                                         &fCodeUnitProperties)) {
   *         return false;
   *     }
   *
   *     // Get some information about trailing spaces / hard line breaks
   *     fTrailingSpaces = fText.size();
   *     TextIndex firstWhitespace = EMPTY_INDEX;
   *     for (int i = 0; i < fCodeUnitProperties.size(); ++i) {
   *         auto flags = fCodeUnitProperties[i];
   *         if (SkUnicode::hasPartOfWhiteSpaceBreakFlag(flags)) {
   *             if (fTrailingSpaces  == fText.size()) {
   *                 fTrailingSpaces = i;
   *             }
   *             if (firstWhitespace == EMPTY_INDEX) {
   *                 firstWhitespace = i;
   *             }
   *         } else {
   *             fTrailingSpaces = fText.size();
   *         }
   *         if (SkUnicode::hasHardLineBreakFlag(flags)) {
   *             fHasLineBreaks = true;
   *         }
   *     }
   *
   *     if (firstWhitespace < fTrailingSpaces) {
   *         fHasWhitespacesInside = true;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun computeCodeUnitProperties(): Boolean {
    TODO("Implement computeCodeUnitProperties")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::applySpacingAndBuildClusterTable() {
   *
   *     // Check all text styles to see what we have to do (if anything)
   *     size_t letterSpacingStyles = 0;
   *     bool hasWordSpacing = false;
   *     for (auto& block : fTextStyles) {
   *         if (block.fRange.width() > 0) {
   *             if (!SkScalarNearlyZero(block.fStyle.getLetterSpacing())) {
   *                 ++letterSpacingStyles;
   *             }
   *             if (!SkScalarNearlyZero(block.fStyle.getWordSpacing())) {
   *                 hasWordSpacing = true;
   *             }
   *         }
   *     }
   *
   *     if (letterSpacingStyles == 0 && !hasWordSpacing) {
   *         // We don't have to do anything about spacing (most common case)
   *         this->buildClusterTable();
   *         return;
   *     }
   *
   *     if (letterSpacingStyles == 1 && !hasWordSpacing && fTextStyles.size() == 1 &&
   *         fTextStyles[0].fRange.width() == fText.size() && fRuns.size() == 1) {
   *         // We have to letter space the entire paragraph (second most common case)
   *         auto& run = fRuns[0];
   *         auto& style = fTextStyles[0].fStyle;
   *         run.addLetterSpacesEvenly(style.getLetterSpacing());
   *
   *         this->buildClusterTable();
   *
   *         // This is something Flutter requires
   *         for (auto& cluster : fClusters) {
   *             cluster.setHalfLetterSpacing(style.getLetterSpacing() / 2);
   *         }
   *
   *         return;
   *     }
   *
   *     // The complex case: many text styles with spacing (possibly not adjusted to glyphs)
   *     this->buildClusterTable();
   *
   *     // Walk through all the clusters in the direction of shaped text
   *     // (we have to walk through the styles in the same order, too)
   *     // Not breaking the iteration on every run!
   *     SkScalar shift = 0;
   *     bool soFarWhitespacesOnly = true;
   *     bool wordSpacingPending = false;
   *     Cluster* lastSpaceCluster = nullptr;
   *     for (auto& run : fRuns) {
   *
   *         // Skip placeholder runs
   *         if (run.isPlaceholder()) {
   *             continue;
   *         }
   *
   *         run.iterateThroughClusters([this, &run, &shift, &soFarWhitespacesOnly, &wordSpacingPending, &lastSpaceCluster](Cluster* cluster) {
   *             // Shift the cluster (shift collected from the previous clusters)
   *             run.shift(cluster, shift);
   *
   *             // Synchronize styles (one cluster can be covered by few styles)
   *             Block* currentStyle = fTextStyles.begin();
   *             while (!cluster->startsIn(currentStyle->fRange)) {
   *                 currentStyle++;
   *                 SkASSERT(currentStyle != fTextStyles.end());
   *             }
   *
   *             SkASSERT(!currentStyle->fStyle.isPlaceholder());
   *
   *             // Process word spacing
   *             if (currentStyle->fStyle.getWordSpacing() != 0) {
   *                 if (cluster->isWhitespaceBreak() && cluster->isSoftBreak()) {
   *                     if (!soFarWhitespacesOnly) {
   *                         lastSpaceCluster = cluster;
   *                         wordSpacingPending = true;
   *                     }
   *                 } else if (wordSpacingPending) {
   *                     SkScalar spacing = currentStyle->fStyle.getWordSpacing();
   *                     if (cluster->fRunIndex != lastSpaceCluster->fRunIndex) {
   *                         // If the last space cluster belongs to the previous run
   *                         // we have to extend that cluster and that run
   *                         lastSpaceCluster->run().addSpacesAtTheEnd(spacing, lastSpaceCluster);
   *                         lastSpaceCluster->run().extend(lastSpaceCluster, spacing);
   *                     } else {
   *                         run.addSpacesAtTheEnd(spacing, lastSpaceCluster);
   *                     }
   *
   *                     run.shift(cluster, spacing);
   *                     shift += spacing;
   *                     wordSpacingPending = false;
   *                 }
   *             }
   *             // Process letter spacing (it will be cancelled out for Script languages
   *             if ((currentStyle->fStyle.getLetterSpacing() != 0)) {
   *                 shift +=
   *                         run.addLetterSpacesEvenly(currentStyle->fStyle.getLetterSpacing(), cluster);
   *             }
   *
   *             if (soFarWhitespacesOnly && !cluster->isWhitespaceBreak()) {
   *                 soFarWhitespacesOnly = false;
   *             }
   *         });
   *     }
   * }
   * ```
   */
  public fun applySpacingAndBuildClusterTable() {
    TODO("Implement applySpacingAndBuildClusterTable")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::buildClusterTable() {
   *     // It's possible that one grapheme includes few runs; we cannot handle it
   *     // so we break graphemes by the runs instead
   *     // It's not the ideal solution and has to be revisited later
   *     int cluster_count = 1;
   *     for (auto& run : fRuns) {
   *         cluster_count += run.isPlaceholder() ? 1 : run.size();
   *         fCodeUnitProperties[run.fTextRange.start] |= SkUnicode::CodeUnitFlags::kGraphemeStart;
   *         fCodeUnitProperties[run.fTextRange.start] |= SkUnicode::CodeUnitFlags::kGlyphClusterStart;
   *     }
   *     if (!fRuns.empty()) {
   *         fCodeUnitProperties[fRuns.back().textRange().end] |= SkUnicode::CodeUnitFlags::kGraphemeStart;
   *         fCodeUnitProperties[fRuns.back().textRange().end] |= SkUnicode::CodeUnitFlags::kGlyphClusterStart;
   *     }
   *     fClusters.reserve_exact(fClusters.size() + cluster_count);
   *
   *     // Walk through all the run in the direction of input text
   *     for (auto& run : fRuns) {
   *         auto runIndex = run.index();
   *         auto runStart = fClusters.size();
   *         if (run.isPlaceholder()) {
   *             // Add info to cluster indexes table (text -> cluster)
   *             for (auto i = run.textRange().start; i < run.textRange().end; ++i) {
   *               fClustersIndexFromCodeUnit[i] = fClusters.size();
   *             }
   *             // There are no glyphs but we want to have one cluster
   *             fClusters.emplace_back(this, runIndex, 0ul, 1ul, this->text(run.textRange()), run.advance().fX, run.advance().fY);
   *             fCodeUnitProperties[run.textRange().start] |= SkUnicode::CodeUnitFlags::kSoftLineBreakBefore;
   *             fCodeUnitProperties[run.textRange().end] |= SkUnicode::CodeUnitFlags::kSoftLineBreakBefore;
   *         } else {
   *             // Walk through the glyph in the direction of input text
   *             run.iterateThroughClustersInTextOrder([runIndex, this](size_t glyphStart,
   *                                                                    size_t glyphEnd,
   *                                                                    size_t charStart,
   *                                                                    size_t charEnd,
   *                                                                    SkScalar width,
   *                                                                    SkScalar height) {
   *                 SkASSERT(charEnd >= charStart);
   *                 // Add info to cluster indexes table (text -> cluster)
   *                 for (auto i = charStart; i < charEnd; ++i) {
   *                   fClustersIndexFromCodeUnit[i] = fClusters.size();
   *                 }
   *                 SkSpan<const char> text(fText.c_str() + charStart, charEnd - charStart);
   *                 fClusters.emplace_back(this, runIndex, glyphStart, glyphEnd, text, width, height);
   *                 fCodeUnitProperties[charStart] |= SkUnicode::CodeUnitFlags::kGlyphClusterStart;
   *             });
   *         }
   *         fCodeUnitProperties[run.textRange().start] |= SkUnicode::CodeUnitFlags::kGlyphClusterStart;
   *
   *         run.setClusterRange(runStart, fClusters.size());
   *         fMaxIntrinsicWidth += run.advance().fX;
   *     }
   *     fClustersIndexFromCodeUnit[fText.size()] = fClusters.size();
   *     fClusters.emplace_back(this, EMPTY_RUN, 0, 0, this->text({fText.size(), fText.size()}), 0, 0);
   * }
   * ```
   */
  public fun buildClusterTable() {
    TODO("Implement buildClusterTable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::shapeTextIntoEndlessLine() {
   *
   *     if (fText.size() == 0) {
   *         return false;
   *     }
   *
   *     fUnresolvedCodepoints.clear();
   *     fFontSwitches.clear();
   *
   *     OneLineShaper oneLineShaper(this);
   *     auto result = oneLineShaper.shape();
   *     fUnresolvedGlyphs = oneLineShaper.unresolvedGlyphs();
   *
   *     this->applySpacingAndBuildClusterTable();
   *
   *     return result;
   * }
   * ```
   */
  public fun shapeTextIntoEndlessLine(): Boolean {
    TODO("Implement shapeTextIntoEndlessLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::breakShapedTextIntoLines(SkScalar maxWidth) {
   *
   *     if (!fHasLineBreaks &&
   *         !fHasWhitespacesInside &&
   *         fPlaceholders.size() == 1 &&
   *         fRuns.size() == 1 && fRuns[0].fAdvance.fX <= maxWidth) {
   *         // This is a short version of a line breaking when we know that:
   *         // 1. We have only one line of text
   *         // 2. It's shaped into a single run
   *         // 3. There are no placeholders
   *         // 4. There are no linebreaks (which will format text into multiple lines)
   *         // 5. There are no whitespaces so the minIntrinsicWidth=maxIntrinsicWidth
   *         // (To think about that, the last condition is not quite right;
   *         // we should calculate minIntrinsicWidth by soft line breaks.
   *         // However, it's how it's done in Flutter now)
   *         auto& run = this->fRuns[0];
   *         auto advance = run.advance();
   *         auto textRange = TextRange(0, this->text().size());
   *         auto textExcludingSpaces = TextRange(0, fTrailingSpaces);
   *         InternalLineMetrics metrics(this->strutForceHeight());
   *         metrics.add(&run);
   *         auto disableFirstAscent = this->paragraphStyle().getTextHeightBehavior() &
   *                                   TextHeightBehavior::kDisableFirstAscent;
   *         auto disableLastDescent = this->paragraphStyle().getTextHeightBehavior() &
   *                                   TextHeightBehavior::kDisableLastDescent;
   *         if (disableFirstAscent) {
   *             metrics.fAscent = metrics.fRawAscent;
   *         }
   *         if (disableLastDescent) {
   *             metrics.fDescent = metrics.fRawDescent;
   *         }
   *         if (this->strutEnabled()) {
   *             this->strutMetrics().updateLineMetrics(metrics);
   *         }
   *         ClusterIndex trailingSpaces = fClusters.size();
   *         do {
   *             --trailingSpaces;
   *             auto& cluster = fClusters[trailingSpaces];
   *             if (!cluster.isWhitespaceBreak()) {
   *                 ++trailingSpaces;
   *                 break;
   *             }
   *             advance.fX -= cluster.width();
   *         } while (trailingSpaces != 0);
   *
   *         advance.fY = metrics.height();
   *         auto clusterRange = ClusterRange(0, trailingSpaces);
   *         auto clusterRangeWithGhosts = ClusterRange(0, this->clusters().size() - 1);
   *         this->addLine(SkPoint::Make(0, 0), advance,
   *                       textExcludingSpaces, textRange, textRange,
   *                       clusterRange, clusterRangeWithGhosts, run.advance().x(),
   *                       metrics);
   *
   *         fLongestLine = nearlyZero(advance.fX) ? run.advance().fX : advance.fX;
   *         fHeight = advance.fY;
   *         fWidth = maxWidth;
   *         fMaxIntrinsicWidth = run.advance().fX;
   *         fMinIntrinsicWidth = advance.fX;
   *         fAlphabeticBaseline = fLines.empty() ? fEmptyMetrics.alphabeticBaseline() : fLines.front().alphabeticBaseline();
   *         fIdeographicBaseline = fLines.empty() ? fEmptyMetrics.ideographicBaseline() : fLines.front().ideographicBaseline();
   *         fExceededMaxLines = false;
   *         return;
   *     }
   *
   *     TextWrapper textWrapper;
   *     textWrapper.breakTextIntoLines(
   *             this,
   *             maxWidth,
   *             [&](TextRange textExcludingSpaces,
   *                 TextRange text,
   *                 TextRange textWithNewlines,
   *                 ClusterRange clusters,
   *                 ClusterRange clustersWithGhosts,
   *                 SkScalar widthWithSpaces,
   *                 size_t startPos,
   *                 size_t endPos,
   *                 SkVector offset,
   *                 SkVector advance,
   *                 InternalLineMetrics metrics,
   *                 bool addEllipsis) {
   *                 // TODO: Take in account clipped edges
   *                 auto& line = this->addLine(offset, advance, textExcludingSpaces, text, textWithNewlines, clusters, clustersWithGhosts, widthWithSpaces, metrics);
   *                 if (addEllipsis) {
   *                     line.createEllipsis(maxWidth, this->getEllipsis(), true);
   *                 }
   *                 fLongestLine = std::max(fLongestLine, nearlyZero(line.width()) ? widthWithSpaces : line.width());
   *             });
   *
   *     fHeight = textWrapper.height();
   *     fWidth = maxWidth;
   *     fMaxIntrinsicWidth = textWrapper.maxIntrinsicWidth();
   *     fMinIntrinsicWidth = textWrapper.minIntrinsicWidth();
   *     fAlphabeticBaseline = fLines.empty() ? fEmptyMetrics.alphabeticBaseline() : fLines.front().alphabeticBaseline();
   *     fIdeographicBaseline = fLines.empty() ? fEmptyMetrics.ideographicBaseline() : fLines.front().ideographicBaseline();
   *     fExceededMaxLines = textWrapper.exceededMaxLines();
   * }
   * ```
   */
  public fun breakShapedTextIntoLines(maxWidth: SkScalar) {
    TODO("Implement breakShapedTextIntoLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::updateTextAlign(TextAlign textAlign) {
   *     fParagraphStyle.setTextAlign(textAlign);
   *
   *     if (fState >= kLineBroken) {
   *         fState = kLineBroken;
   *     }
   * }
   * ```
   */
  public override fun updateTextAlign(textAlign: TextAlign) {
    TODO("Implement updateTextAlign")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::updateFontSize(size_t from, size_t to, SkScalar fontSize) {
   *
   *   SkASSERT(from == 0 && to == fText.size());
   *   auto defaultStyle = fParagraphStyle.getTextStyle();
   *   defaultStyle.setFontSize(fontSize);
   *   fParagraphStyle.setTextStyle(defaultStyle);
   *
   *   for (auto& textStyle : fTextStyles) {
   *     textStyle.fStyle.setFontSize(fontSize);
   *   }
   *
   *   fState = std::min(fState, kIndexed);
   *   fOldWidth = 0;
   *   fOldHeight = 0;
   * }
   * ```
   */
  public override fun updateFontSize(
    from: ULong,
    to: ULong,
    fontSize: SkScalar,
  ) {
    TODO("Implement updateFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::updateForegroundPaint(size_t from, size_t to, SkPaint paint) {
   *     SkASSERT(from == 0 && to == fText.size());
   *     auto defaultStyle = fParagraphStyle.getTextStyle();
   *     defaultStyle.setForegroundColor(paint);
   *     fParagraphStyle.setTextStyle(defaultStyle);
   *
   *     for (auto& textStyle : fTextStyles) {
   *         textStyle.fStyle.setForegroundColor(paint);
   *     }
   * }
   * ```
   */
  public override fun updateForegroundPaint(
    from: ULong,
    to: ULong,
    paint: SkPaint,
  ) {
    TODO("Implement updateForegroundPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::updateBackgroundPaint(size_t from, size_t to, SkPaint paint) {
   *     SkASSERT(from == 0 && to == fText.size());
   *     auto defaultStyle = fParagraphStyle.getTextStyle();
   *     defaultStyle.setBackgroundColor(paint);
   *     fParagraphStyle.setTextStyle(defaultStyle);
   *
   *     for (auto& textStyle : fTextStyles) {
   *         textStyle.fStyle.setBackgroundColor(paint);
   *     }
   * }
   * ```
   */
  public override fun updateBackgroundPaint(
    from: ULong,
    to: ULong,
    paint: SkPaint,
  ) {
    TODO("Implement updateBackgroundPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::visit(const Visitor& visitor) {
   *     int lineNumber = 0;
   *     for (auto& line : fLines) {
   *         line.ensureTextBlobCachePopulated();
   *         for (auto& rec : line.fTextBlobCache) {
   *             if (rec.fBlob == nullptr) {
   *                 continue;
   *             }
   *             SkTextBlob::Iter iter(*rec.fBlob);
   *             SkTextBlob::Iter::ExperimentalRun run;
   *
   *             STArray<128, uint32_t> clusterStorage;
   *             const Run* R = rec.fVisitor_Run;
   *             const uint32_t* clusterPtr = &R->fClusterIndexes[0];
   *
   *             if (R->fClusterStart > 0) {
   *                 int count = R->fClusterIndexes.size();
   *                 clusterStorage.reset(count);
   *                 for (int i = 0; i < count; ++i) {
   *                     clusterStorage[i] = R->fClusterStart + R->fClusterIndexes[i];
   *                 }
   *                 clusterPtr = &clusterStorage[0];
   *             }
   *             clusterPtr += rec.fVisitor_Pos;
   *
   *             while (iter.experimentalNext(&run)) {
   *                 const Paragraph::VisitorInfo info = {
   *                     run.font,
   *                     rec.fOffset,
   *                     rec.fClipRect.fRight,
   *                     run.count,
   *                     run.glyphs,
   *                     run.positions,
   *                     clusterPtr,
   *                     0,  // flags
   *                 };
   *                 visitor(lineNumber, &info);
   *                 clusterPtr += run.count;
   *             }
   *         }
   *         visitor(lineNumber, nullptr);   // signal end of line
   *         lineNumber += 1;
   *     }
   * }
   * ```
   */
  public override fun visit(visitor: Visitor) {
    TODO("Implement visit")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::extendedVisit(const ExtendedVisitor& visitor) {
   *     int lineNumber = 0;
   *     for (auto& line : fLines) {
   *         line.iterateThroughVisualRuns(
   *             false,
   *             [&](const Run* run,
   *                 SkScalar runOffsetInLine,
   *                 TextRange textRange,
   *                 SkScalar* runWidthInLine) {
   *                 *runWidthInLine = line.iterateThroughSingleRunByStyles(
   *                 TextLine::TextAdjustment::GlyphCluster,
   *                 run,
   *                 runOffsetInLine,
   *                 textRange,
   *                 StyleType::kNone,
   *                 [&](TextRange textRange,
   *                     const TextStyle& style,
   *                     const TextLine::ClipContext& context) {
   *                     SkScalar correctedBaseline = SkScalarFloorToScalar(
   *                         line.baseline() + style.getBaselineShift() + 0.5);
   *                     SkPoint offset =
   *                         SkPoint::Make(line.offset().fX + context.fTextShift,
   *                                       line.offset().fY + correctedBaseline);
   *                     SkRect rect = context.clip.makeOffset(line.offset());
   *                     AutoSTArray<16, SkRect> glyphBounds;
   *                     glyphBounds.reset(SkToInt(run->size()));
   *                     run->font().getBounds(run->glyphs(), glyphBounds, nullptr);
   *                     STArray<128, uint32_t> clusterStorage;
   *                     const uint32_t* clusterPtr = run->clusterIndexes().data();
   *                     if (run->fClusterStart > 0) {
   *                         clusterStorage.reset(context.size);
   *                         for (size_t i = 0; i < context.size; ++i) {
   *                           clusterStorage[i] =
   *                               run->fClusterStart + run->fClusterIndexes[i];
   *                         }
   *                         clusterPtr = &clusterStorage[0];
   *                     }
   *                     const Paragraph::ExtendedVisitorInfo info = {
   *                         run->font(),
   *                         offset,
   *                         SkSize::Make(rect.width(), rect.height()),
   *                         SkToS16(context.size),
   *                         &run->glyphs()[context.pos],
   *                         &run->fPositions[context.pos],
   *                         &glyphBounds[context.pos],
   *                         clusterPtr,
   *                         0,  // flags
   *                     };
   *                     visitor(lineNumber, &info);
   *                 });
   *             return true;
   *             });
   *         visitor(lineNumber, nullptr);   // signal end of line
   *         lineNumber += 1;
   *     }
   * }
   * ```
   */
  public override fun extendedVisit(visitor: ExtendedVisitor) {
    TODO("Implement extendedVisit")
  }

  /**
   * C++ original:
   * ```cpp
   * int ParagraphImpl::getPath(int lineNumber, SkPath* dest) {
   *     SkPathBuilder builder;
   *     int notConverted = 0;
   *     auto& line = fLines[lineNumber];
   *     line.iterateThroughVisualRuns(
   *               false,
   *               [&](const Run* run,
   *                   SkScalar runOffsetInLine,
   *                   TextRange textRange,
   *                   SkScalar* runWidthInLine) {
   *           *runWidthInLine = line.iterateThroughSingleRunByStyles(
   *           TextLine::TextAdjustment::GlyphCluster,
   *           run,
   *           runOffsetInLine,
   *           textRange,
   *           StyleType::kNone,
   *           [&](TextRange textRange,
   *               const TextStyle& style,
   *               const TextLine::ClipContext& context) {
   *               const SkFont& font = run->font();
   *               SkScalar correctedBaseline = SkScalarFloorToScalar(
   *                 line.baseline() + style.getBaselineShift() + 0.5);
   *               SkPoint offset =
   *                   SkPoint::Make(line.offset().fX + context.fTextShift,
   *                                 line.offset().fY + correctedBaseline);
   *               SkRect rect = context.clip.makeOffset(offset);
   *               struct Rec {
   *                   SkPathBuilder* fBuilder;
   *                   SkPoint fOffset;
   *                   const SkPoint* fPos;
   *                   int fNotConverted;
   *               } rec =
   *                   {&builder, SkPoint::Make(rect.left(), rect.top()), &run->positions()[context.pos], 0};
   *               font.getPaths({&run->glyphs()[context.pos], context.size},
   *                     [](const SkPath* path, const SkMatrix& mx, void* ctx) {
   *                         Rec* rec = reinterpret_cast<Rec*>(ctx);
   *                         if (path) {
   *                             SkMatrix total = mx;
   *                             total.postTranslate(rec->fPos->fX + rec->fOffset.fX,
   *                                                 rec->fPos->fY + rec->fOffset.fY);
   *                             rec->fBuilder->addPath(*path, total);
   *                         } else {
   *                             rec->fNotConverted++;
   *                         }
   *                         rec->fPos += 1; // move to the next glyph's position
   *                     }, &rec);
   *               notConverted += rec.fNotConverted;
   *           });
   *         *dest = builder.detach();
   *         return true;
   *     });
   *
   *     return notConverted;
   * }
   * ```
   */
  public override fun getPath(lineNumber: Int, dest: SkPath?): Int {
    TODO("Implement getPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::containsColorFontOrBitmap(SkTextBlob* textBlob) {
   *     SkTextBlobRunIterator iter(textBlob);
   *     bool flag = false;
   *     while (!iter.done() && !flag) {
   *         iter.font().getPaths(
   *             {(const SkGlyphID*) iter.glyphs(), iter.glyphCount()},
   *             [](const SkPath* path, const SkMatrix& mx, void* ctx) {
   *                 if (path == nullptr) {
   *                     bool* flag1 = (bool*)ctx;
   *                     *flag1 = true;
   *                 }
   *             }, &flag);
   *         iter.next();
   *     }
   *     return flag;
   * }
   * ```
   */
  public override fun containsColorFontOrBitmap(textBlob: SkTextBlob?): Boolean {
    TODO("Implement containsColorFontOrBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::containsEmoji(SkTextBlob* textBlob) {
   *     bool result = false;
   *     SkTextBlobRunIterator iter(textBlob);
   *     while (!iter.done() && !result) {
   *         // Walk through all the text by codepoints
   *         this->getUnicode()->forEachCodepoint(iter.text(), iter.textSize(),
   *            [&](SkUnichar unichar, int32_t start, int32_t end, int32_t count) {
   *                 if (this->getUnicode()->isEmoji(unichar)) {
   *                     result = true;
   *                 }
   *             });
   *         iter.next();
   *     }
   *     return result;
   * }
   * ```
   */
  public override fun containsEmoji(textBlob: SkTextBlob?): Boolean {
    TODO("Implement containsEmoji")
  }

  /**
   * C++ original:
   * ```cpp
   * int ParagraphImpl::getLineNumberAt(TextIndex codeUnitIndex) const {
   *     if (codeUnitIndex >= fText.size()) {
   *         return -1;
   *     }
   *     size_t startLine = 0;
   *     size_t endLine = fLines.size() - 1;
   *     if (fLines.empty() || fLines[endLine].textWithNewlines().end <= codeUnitIndex) {
   *         return -1;
   *     }
   *
   *     while (endLine > startLine) {
   *         // startLine + 1 <= endLine, so we have startLine <= midLine <= endLine - 1.
   *         const size_t midLine = (endLine + startLine) / 2;
   *         const TextRange midLineRange = fLines[midLine].textWithNewlines();
   *         if (codeUnitIndex < midLineRange.start) {
   *             endLine = midLine - 1;
   *         } else if (midLineRange.end <= codeUnitIndex) {
   *             startLine = midLine + 1;
   *         } else {
   *             return midLine;
   *         }
   *     }
   *     SkASSERT(startLine == endLine);
   *     return startLine;
   * }
   * ```
   */
  public override fun getLineNumberAt(codeUnitIndex: TextIndex): Int {
    TODO("Implement getLineNumberAt")
  }

  /**
   * C++ original:
   * ```cpp
   * int ParagraphImpl::getLineNumberAtUTF16Offset(size_t codeUnitIndex) {
   *     this->ensureUTF16Mapping();
   *     if (codeUnitIndex >= SkToSizeT(fUTF8IndexForUTF16Index.size())) {
   *         return -1;
   *     }
   *     const TextIndex utf8 = fUTF8IndexForUTF16Index[codeUnitIndex];
   *     return getLineNumberAt(utf8);
   * }
   * ```
   */
  public override fun getLineNumberAtUTF16Offset(codeUnitIndex: ULong): Int {
    TODO("Implement getLineNumberAtUTF16Offset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::getLineMetricsAt(int lineNumber, LineMetrics* lineMetrics) const {
   *     if (lineNumber < 0 || lineNumber >= fLines.size()) {
   *         return false;
   *     }
   *     auto& line = fLines[lineNumber];
   *     if (lineMetrics) {
   *         *lineMetrics = line.getMetrics();
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun getLineMetricsAt(lineNumber: Int, lineMetrics: LineMetrics?): Boolean {
    TODO("Implement getLineMetricsAt")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange ParagraphImpl::getActualTextRange(int lineNumber, bool includeSpaces) const {
   *     if (lineNumber < 0 || lineNumber >= fLines.size()) {
   *         return EMPTY_TEXT;
   *     }
   *     auto& line = fLines[lineNumber];
   *     return includeSpaces ? line.text() : line.trimmedText();
   * }
   * ```
   */
  public override fun getActualTextRange(lineNumber: Int, includeSpaces: Boolean): Int {
    TODO("Implement getActualTextRange")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::getGlyphClusterAt(TextIndex codeUnitIndex, GlyphClusterInfo* glyphInfo) {
   *     const int lineNumber = getLineNumberAt(codeUnitIndex);
   *     if (lineNumber == -1) {
   *         return false;
   *     }
   *     auto& line = fLines[lineNumber];
   *     for (auto c = line.clustersWithSpaces().start; c < line.clustersWithSpaces().end; ++c) {
   *         auto& cluster = fClusters[c];
   *         if (cluster.contains(codeUnitIndex)) {
   *             std::vector<TextBox> boxes;
   *             line.getRectsForRange(cluster.textRange(),
   *                                     RectHeightStyle::kTight,
   *                                     RectWidthStyle::kTight,
   *                                     boxes);
   *             if (!boxes.empty()) {
   *                 if (glyphInfo) {
   *                     *glyphInfo = {boxes[0].rect, cluster.textRange(), boxes[0].direction};
   *                 }
   *                 return true;
   *             }
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun getGlyphClusterAt(codeUnitIndex: TextIndex, glyphInfo: GlyphClusterInfo?): Boolean {
    TODO("Implement getGlyphClusterAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::getClosestGlyphClusterAt(SkScalar dx,
   *                                              SkScalar dy,
   *                                              GlyphClusterInfo* glyphInfo) {
   *     const PositionWithAffinity res = this->getGlyphPositionAtCoordinate(dx, dy);
   *     SkASSERT(res.position != 0 || res.affinity != Affinity::kUpstream);
   *     const size_t utf16Offset = res.position + (res.affinity == Affinity::kDownstream ? 0 : -1);
   *     this->ensureUTF16Mapping();
   *     SkASSERT(utf16Offset < SkToSizeT(fUTF8IndexForUTF16Index.size()));
   *     return this->getGlyphClusterAt(fUTF8IndexForUTF16Index[utf16Offset], glyphInfo);
   * }
   * ```
   */
  public override fun getClosestGlyphClusterAt(
    dx: SkScalar,
    dy: SkScalar,
    glyphInfo: GlyphClusterInfo?,
  ): Boolean {
    TODO("Implement getClosestGlyphClusterAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::getGlyphInfoAtUTF16Offset(size_t codeUnitIndex, GlyphInfo* glyphInfo) {
   *     this->ensureUTF16Mapping();
   *     if (codeUnitIndex >= SkToSizeT(fUTF8IndexForUTF16Index.size())) {
   *         return false;
   *     }
   *     const TextIndex utf8 = fUTF8IndexForUTF16Index[codeUnitIndex];
   *     const int lineNumber = getLineNumberAt(utf8);
   *     if (lineNumber == -1) {
   *         return false;
   *     }
   *     if (glyphInfo == nullptr) {
   *         return true;
   *     }
   *     const TextLine& line = fLines[lineNumber];
   *     const TextIndex startIndex = findPreviousGraphemeBoundary(utf8);
   *     const TextIndex endIndex = findNextGraphemeBoundary(utf8 + 1);
   *     const ClusterIndex glyphClusterIndex = clusterIndex(utf8);
   *     const Cluster& glyphCluster = cluster(glyphClusterIndex);
   *
   *     // `startIndex` and `endIndex` must be on the same line.
   *     std::vector<TextBox> boxes;
   *     line.getRectsForRange({startIndex, endIndex}, RectHeightStyle::kTight, RectWidthStyle::kTight, boxes);
   *     // TODO: currently placeholders with height=0 and width=0 are ignored so boxes
   *     // can be empty. These placeholders should still be reported for their
   *     // offset information.
   *     if (glyphInfo && !boxes.empty()) {
   *         *glyphInfo = {
   *             boxes[0].rect,
   *             { fUTF16IndexForUTF8Index[startIndex], fUTF16IndexForUTF8Index[endIndex] },
   *             boxes[0].direction,
   *             glyphCluster.run().isEllipsis(),
   *         };
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun getGlyphInfoAtUTF16Offset(codeUnitIndex: ULong, graphemeInfo: GlyphInfo?): Boolean {
    TODO("Implement getGlyphInfoAtUTF16Offset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ParagraphImpl::getClosestUTF16GlyphInfoAt(SkScalar dx, SkScalar dy, GlyphInfo* glyphInfo) {
   *     const PositionWithAffinity res = this->getGlyphPositionAtCoordinate(dx, dy);
   *     SkASSERT(res.position != 0 || res.affinity != Affinity::kUpstream);
   *     const size_t utf16Offset = res.position + (res.affinity == Affinity::kDownstream ? 0 : -1);
   *     return getGlyphInfoAtUTF16Offset(utf16Offset, glyphInfo);
   * }
   * ```
   */
  public override fun getClosestUTF16GlyphInfoAt(
    dx: SkScalar,
    dy: SkScalar,
    graphemeInfo: GlyphInfo?,
  ): Boolean {
    TODO("Implement getClosestUTF16GlyphInfoAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont ParagraphImpl::getFontAt(TextIndex codeUnitIndex) const {
   *     for (auto& run : fRuns) {
   *         const auto textRange = run.textRange();
   *         if (textRange.start <= codeUnitIndex && codeUnitIndex < textRange.end) {
   *             return run.font();
   *         }
   *     }
   *     return SkFont();
   * }
   * ```
   */
  public override fun getFontAt(codeUnitIndex: TextIndex): Int {
    TODO("Implement getFontAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont ParagraphImpl::getFontAtUTF16Offset(size_t codeUnitIndex) {
   *     ensureUTF16Mapping();
   *     if (codeUnitIndex >= SkToSizeT(fUTF8IndexForUTF16Index.size())) {
   *         return SkFont();
   *     }
   *     const TextIndex utf8 = fUTF8IndexForUTF16Index[codeUnitIndex];
   *     for (auto& run : fRuns) {
   *         const auto textRange = run.textRange();
   *         if (textRange.start <= utf8 && utf8 < textRange.end) {
   *             return run.font();
   *         }
   *     }
   *     return SkFont();
   * }
   * ```
   */
  public override fun getFontAtUTF16Offset(codeUnitIndex: ULong): Int {
    TODO("Implement getFontAtUTF16Offset")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<Paragraph::FontInfo> ParagraphImpl::getFonts() const {
   *     std::vector<FontInfo> results;
   *     for (auto& run : fRuns) {
   *         results.emplace_back(run.font(), run.textRange());
   *     }
   *     return results;
   * }
   * ```
   */
  public override fun getFonts(): Int {
    TODO("Implement getFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics getEmptyMetrics() const { return fEmptyMetrics; }
   * ```
   */
  public fun getEmptyMetrics(): Int {
    TODO("Implement getEmptyMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * InternalLineMetrics getStrutMetrics() const { return fStrutMetrics; }
   * ```
   */
  public fun getStrutMetrics(): Int {
    TODO("Implement getStrutMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * BlockRange ParagraphImpl::findAllBlocks(TextRange textRange) {
   *     BlockIndex begin = EMPTY_BLOCK;
   *     BlockIndex end = EMPTY_BLOCK;
   *     for (int index = 0; index < fTextStyles.size(); ++index) {
   *         auto& block = fTextStyles[index];
   *         if (block.fRange.end <= textRange.start) {
   *             continue;
   *         }
   *         if (block.fRange.start >= textRange.end) {
   *             break;
   *         }
   *         if (begin == EMPTY_BLOCK) {
   *             begin = index;
   *         }
   *         end = index;
   *     }
   *
   *     if (begin == EMPTY_INDEX || end == EMPTY_INDEX) {
   *         // It's possible if some text is not covered with any text style
   *         // Not in Flutter but in direct use of SkParagraph
   *         return EMPTY_RANGE;
   *     }
   *
   *     return { begin, end + 1 };
   * }
   * ```
   */
  public fun findAllBlocks(textRange: TextRange): Int {
    TODO("Implement findAllBlocks")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetShifts() {
   *         for (auto& run : fRuns) {
   *             run.resetJustificationShifts();
   *         }
   *     }
   * ```
   */
  public fun resetShifts() {
    TODO("Implement resetShifts")
  }

  /**
   * C++ original:
   * ```cpp
   * bool codeUnitHasProperty(size_t index, SkUnicode::CodeUnitFlags property) const {
   *         return (fCodeUnitProperties[index] & property) == property;
   *     }
   * ```
   */
  public fun codeUnitHasProperty(index: ULong, `property`: SkUnicode.CodeUnitFlags): Boolean {
    TODO("Implement codeUnitHasProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkUnicode> getUnicode() { return fUnicode; }
   * ```
   */
  public fun getUnicode(): Int {
    TODO("Implement getUnicode")
  }

  /**
   * C++ original:
   * ```cpp
   * void ParagraphImpl::computeEmptyMetrics() {
   *
   *     // The empty metrics is used to define the height of the empty lines
   *     // Unfortunately, Flutter has 2 different cases for that:
   *     // 1. An empty line inside the text
   *     // 2. An empty paragraph
   *     // In the first case SkParagraph takes the metrics from the default paragraph style
   *     // In the second case it should take it from the current text style
   *     bool emptyParagraph = fRuns.empty();
   *     TextStyle textStyle = paragraphStyle().getTextStyle();
   *     if (emptyParagraph && !fTextStyles.empty()) {
   *         textStyle = fTextStyles.back().fStyle;
   *     }
   *
   *     auto typefaces = fontCollection()->findTypefaces(
   *       textStyle.getFontFamilies(), textStyle.getFontStyle(), textStyle.getFontArguments());
   *     auto typeface = typefaces.empty() ? nullptr : typefaces.front();
   *
   *     SkFont font(typeface, textStyle.getFontSize());
   *     fEmptyMetrics = InternalLineMetrics(font, paragraphStyle().getStrutStyle().getForceStrutHeight());
   *
   *     if (!paragraphStyle().getStrutStyle().getForceStrutHeight() &&
   *         textStyle.getHeightOverride()) {
   *         const auto intrinsicHeight = fEmptyMetrics.height();
   *         const auto strutHeight = textStyle.getHeight() * textStyle.getFontSize();
   *         if (paragraphStyle().getStrutStyle().getHalfLeading()) {
   *             fEmptyMetrics.update(
   *                 fEmptyMetrics.ascent(),
   *                 fEmptyMetrics.descent(),
   *                 fEmptyMetrics.leading() + strutHeight - intrinsicHeight);
   *         } else {
   *             const auto multiplier = strutHeight / intrinsicHeight;
   *             fEmptyMetrics.update(
   *                 fEmptyMetrics.ascent() * multiplier,
   *                 fEmptyMetrics.descent() * multiplier,
   *                 fEmptyMetrics.leading() * multiplier);
   *         }
   *     }
   *
   *     if (emptyParagraph) {
   *         // For an empty text we apply both TextHeightBehaviour flags
   *         // In case of non-empty paragraph TextHeightBehaviour flags will be applied at the appropriate place
   *         // We have to do it here because we skip wrapping for an empty text
   *         auto disableFirstAscent = (paragraphStyle().getTextHeightBehavior() & TextHeightBehavior::kDisableFirstAscent) == TextHeightBehavior::kDisableFirstAscent;
   *         auto disableLastDescent = (paragraphStyle().getTextHeightBehavior() & TextHeightBehavior::kDisableLastDescent) == TextHeightBehavior::kDisableLastDescent;
   *         fEmptyMetrics.update(
   *             disableFirstAscent ? fEmptyMetrics.rawAscent() : fEmptyMetrics.ascent(),
   *             disableLastDescent ? fEmptyMetrics.rawDescent() : fEmptyMetrics.descent(),
   *             fEmptyMetrics.leading());
   *     }
   *
   *     if (fParagraphStyle.getStrutStyle().getStrutEnabled()) {
   *         fStrutMetrics.updateLineMetrics(fEmptyMetrics);
   *     }
   * }
   * ```
   */
  private fun computeEmptyMetrics() {
    TODO("Implement computeEmptyMetrics")
  }
}
