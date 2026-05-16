package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkTextBlobBuilder
import org.skia.math.SkScalar
import undefined.ClusterVisitor

/**
 * C++ original:
 * ```cpp
 * class Run {
 * public:
 *     Run(ParagraphImpl* owner,
 *         const SkShaper::RunHandler::RunInfo& info,
 *         size_t firstChar,
 *         SkScalar heightMultiplier,
 *         bool useHalfLeading,
 *         SkScalar baselineShift,
 *         size_t index,
 *         SkScalar shiftX);
 *     Run(const Run&) = default;
 *     Run& operator=(const Run&) = delete;
 *     Run(Run&&) = default;
 *     Run& operator=(Run&&) = delete;
 *     ~Run() = default;
 *
 *     void setOwner(ParagraphImpl* owner) { fOwner = owner; }
 *
 *     SkShaper::RunHandler::Buffer newRunBuffer();
 *
 *     SkScalar posX(size_t index) const { return fPositions[index].fX; }
 *     void addX(size_t index, SkScalar shift) { fPositions[index].fX += shift; }
 *     SkScalar posY(size_t index) const { return fPositions[index].fY; }
 *     size_t size() const { return fGlyphs.size(); }
 *     void setWidth(SkScalar width) { fAdvance.fX = width; }
 *     void setHeight(SkScalar height) { fAdvance.fY = height; }
 *     void shift(SkScalar shiftX, SkScalar shiftY) {
 *         fOffset.fX += shiftX;
 *         fOffset.fY += shiftY;
 *     }
 *     SkVector advance() const {
 *         return SkVector::Make(fAdvance.fX, fFontMetrics.fDescent - fFontMetrics.fAscent + fFontMetrics.fLeading);
 *     }
 *     SkVector offset() const { return fOffset; }
 *     SkScalar ascent() const { return fFontMetrics.fAscent + fBaselineShift; }
 *     SkScalar descent() const { return fFontMetrics.fDescent + fBaselineShift; }
 *     SkScalar leading() const { return fFontMetrics.fLeading; }
 *     SkScalar correctAscent() const { return fCorrectAscent + fBaselineShift; }
 *     SkScalar correctDescent() const { return fCorrectDescent + fBaselineShift; }
 *     SkScalar correctLeading() const { return fCorrectLeading; }
 *     const SkFont& font() const { return fFont; }
 *     bool leftToRight() const { return fBidiLevel % 2 == 0; }
 *     TextDirection getTextDirection() const { return leftToRight() ? TextDirection::kLtr : TextDirection::kRtl; }
 *     size_t index() const { return fIndex; }
 *     SkScalar heightMultiplier() const { return fHeightMultiplier; }
 *     bool useHalfLeading() const { return fUseHalfLeading; }
 *     SkScalar baselineShift() const { return fBaselineShift; }
 *     PlaceholderStyle* placeholderStyle() const;
 *     bool isPlaceholder() const { return fPlaceholderIndex != std::numeric_limits<size_t>::max(); }
 *     size_t clusterIndex(size_t pos) const { return fClusterIndexes[pos]; }
 *     size_t globalClusterIndex(size_t pos) const { return fClusterStart + fClusterIndexes[pos]; }
 *     SkScalar positionX(size_t pos) const;
 *
 *     TextRange textRange() const { return fTextRange; }
 *     ClusterRange clusterRange() const { return fClusterRange; }
 *
 *     ParagraphImpl* owner() const { return fOwner; }
 *
 *     bool isEllipsis() const { return fEllipsis; }
 *
 *     void calculateMetrics();
 *     void updateMetrics(InternalLineMetrics* endlineMetrics);
 *
 *     void setClusterRange(size_t from, size_t to) { fClusterRange = ClusterRange(from, to); }
 *     SkRect clip() const {
 *         return SkRect::MakeXYWH(fOffset.fX, fOffset.fY, fAdvance.fX, fAdvance.fY);
 *     }
 *
 *     bool isCursiveScript() const;
 *
 *     void addSpacesAtTheEnd(SkScalar space, Cluster* cluster);
 *     SkScalar addLetterSpacesEvenly(SkScalar space, Cluster* cluster);
 *     SkScalar addLetterSpacesEvenly(SkScalar space);
 *     void shift(const Cluster* cluster, SkScalar offset);
 *     void extend(const Cluster* cluster, SkScalar offset);
 *
 *     SkScalar calculateHeight(LineMetricStyle ascentStyle, LineMetricStyle descentStyle) const {
 *         auto ascent = ascentStyle == LineMetricStyle::Typographic ? this->ascent()
 *                                     : this->correctAscent();
 *         auto descent = descentStyle == LineMetricStyle::Typographic ? this->descent()
 *                                       : this->correctDescent();
 *         return descent - ascent;
 *     }
 *     SkScalar calculateWidth(size_t start, size_t end, bool clip) const;
 *
 *     void copyTo(SkTextBlobBuilder& builder, size_t pos, size_t size) const;
 *
 *     template<typename Visitor>
 *     void iterateThroughClustersInTextOrder(Visitor visitor);
 *
 *     using ClusterVisitor = std::function<void(Cluster* cluster)>;
 *     void iterateThroughClusters(const ClusterVisitor& visitor);
 *
 *     std::tuple<bool, ClusterIndex, ClusterIndex> findLimitingClusters(TextRange text) const;
 *     std::tuple<bool, TextIndex, TextIndex> findLimitingGlyphClusters(TextRange text) const;
 *     std::tuple<bool, TextIndex, TextIndex> findLimitingGraphemes(TextRange text) const;
 *     SkSpan<const SkGlyphID> glyphs() const {
 *         return SkSpan<const SkGlyphID>(fGlyphs.begin(), fGlyphs.size());
 *     }
 *     SkSpan<const SkPoint> positions() const {
 *         return SkSpan<const SkPoint>(fPositions.begin(), fPositions.size());
 *     }
 *     SkSpan<const SkPoint> offsets() const {
 *         return SkSpan<const SkPoint>(fOffsets.begin(), fOffsets.size());
 *     }
 *     SkSpan<const uint32_t> clusterIndexes() const {
 *         return SkSpan<const uint32_t>(fClusterIndexes.begin(), fClusterIndexes.size());
 *     }
 *
 *     void commit() { }
 *
 *     void resetJustificationShifts() {
 *         fJustificationShifts.clear();
 *     }
 *
 *     bool isResolved() const;
 * private:
 *     friend class ParagraphImpl;
 *     friend class TextLine;
 *     friend class InternalLineMetrics;
 *     friend class ParagraphCache;
 *     friend class OneLineShaper;
 *
 *     ParagraphImpl* fOwner;
 *     TextRange fTextRange;
 *     ClusterRange fClusterRange;
 *
 *     SkFont fFont;
 *     size_t fPlaceholderIndex;
 *     size_t fIndex;
 *     SkVector fAdvance;
 *     SkVector fOffset;
 *     TextIndex fClusterStart;
 *     SkShaper::RunHandler::Range fUtf8Range;
 *
 *     // These fields are not modified after shaping completes and can safely be
 *     // shared among copies of the run that are held by different paragraphs.
 *     struct GlyphData {
 *         skia_private::STArray<64, SkGlyphID, true> glyphs;
 *         skia_private::STArray<64, SkPoint, true> positions;
 *         skia_private::STArray<64, SkPoint, true> offsets;
 *         skia_private::STArray<64, uint32_t, true> clusterIndexes;
 *     };
 *     std::shared_ptr<GlyphData> fGlyphData;
 *     skia_private::STArray<64, SkGlyphID, true>& fGlyphs;
 *     skia_private::STArray<64, SkPoint, true>& fPositions;
 *     skia_private::STArray<64, SkPoint, true>& fOffsets;
 *     skia_private::STArray<64, uint32_t, true>& fClusterIndexes;
 *
 *     skia_private::STArray<64, SkPoint, true> fJustificationShifts; // For justification
 *                                                                    // (current and prev shifts)
 *
 *     SkFontMetrics fFontMetrics;
 *     const SkScalar fHeightMultiplier;
 *     const bool fUseHalfLeading;
 *     const SkScalar fBaselineShift;
 *     SkScalar fCorrectAscent;
 *     SkScalar fCorrectDescent;
 *     SkScalar fCorrectLeading;
 *
 *     bool fEllipsis;
 *     uint8_t fBidiLevel;
 *     SkFourByteTag fScript;
 *     SkString fLanguage;
 * }
 * ```
 */
public data class Run public constructor(
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
   * TextRange fTextRange
   * ```
   */
  private var fTextRange: Int,
  /**
   * C++ original:
   * ```cpp
   * ClusterRange fClusterRange
   * ```
   */
  private var fClusterRange: ClusterRange,
  /**
   * C++ original:
   * ```cpp
   * SkFont fFont
   * ```
   */
  private var fFont: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fPlaceholderIndex
   * ```
   */
  private var fPlaceholderIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fIndex
   * ```
   */
  private var fIndex: Int,
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
   * TextIndex fClusterStart
   * ```
   */
  private var fClusterStart: Int,
  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Range fUtf8Range
   * ```
   */
  private var fUtf8Range: Int,
  /**
   * C++ original:
   * ```cpp
   * std::shared_ptr<GlyphData> fGlyphData
   * ```
   */
  private var fGlyphData: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<64, SkGlyphID, true>& fGlyphs
   * ```
   */
  private var fGlyphs: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<64, SkPoint, true>& fPositions
   * ```
   */
  private var fPositions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<64, SkPoint, true>& fOffsets
   * ```
   */
  private var fOffsets: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<64, uint32_t, true>& fClusterIndexes
   * ```
   */
  private var fClusterIndexes: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<64, SkPoint, true> fJustificationShifts
   * ```
   */
  private var fJustificationShifts: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFontMetrics fFontMetrics
   * ```
   */
  private var fFontMetrics: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fHeightMultiplier
   * ```
   */
  private val fHeightMultiplier: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool fUseHalfLeading
   * ```
   */
  private val fUseHalfLeading: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fBaselineShift
   * ```
   */
  private val fBaselineShift: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fCorrectAscent
   * ```
   */
  private var fCorrectAscent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fCorrectDescent
   * ```
   */
  private var fCorrectDescent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fCorrectLeading
   * ```
   */
  private var fCorrectLeading: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fEllipsis
   * ```
   */
  private var fEllipsis: Boolean,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fBidiLevel
   * ```
   */
  private var fBidiLevel: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFourByteTag fScript
   * ```
   */
  private var fScript: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString fLanguage
   * ```
   */
  private var fLanguage: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Run& operator=(const Run&) = delete
   * ```
   */
  public fun assign(param0: Run) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Run& operator=(Run&&) = delete
   * ```
   */
  public fun setOwner(owner: ParagraphImpl?) {
    TODO("Implement setOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOwner(ParagraphImpl* owner) { fOwner = owner; }
   * ```
   */
  public fun newRunBuffer(): Int {
    TODO("Implement newRunBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Buffer Run::newRunBuffer() {
   *     return {fGlyphs.data(), fPositions.data(), fOffsets.data(), fClusterIndexes.data(), fOffset};
   * }
   * ```
   */
  public fun posX(index: ULong): Int {
    TODO("Implement posX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar posX(size_t index) const { return fPositions[index].fX; }
   * ```
   */
  public fun addX(index: ULong, shift: SkScalar) {
    TODO("Implement addX")
  }

  /**
   * C++ original:
   * ```cpp
   * void addX(size_t index, SkScalar shift) { fPositions[index].fX += shift; }
   * ```
   */
  public fun posY(index: ULong): Int {
    TODO("Implement posY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar posY(size_t index) const { return fPositions[index].fY; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fGlyphs.size(); }
   * ```
   */
  public fun setWidth(width: SkScalar) {
    TODO("Implement setWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWidth(SkScalar width) { fAdvance.fX = width; }
   * ```
   */
  public fun setHeight(height: SkScalar) {
    TODO("Implement setHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeight(SkScalar height) { fAdvance.fY = height; }
   * ```
   */
  public fun shift(shiftX: SkScalar, shiftY: SkScalar) {
    TODO("Implement shift")
  }

  /**
   * C++ original:
   * ```cpp
   * void shift(SkScalar shiftX, SkScalar shiftY) {
   *         fOffset.fX += shiftX;
   *         fOffset.fY += shiftY;
   *     }
   * ```
   */
  public fun advance(): Int {
    TODO("Implement advance")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector advance() const {
   *         return SkVector::Make(fAdvance.fX, fFontMetrics.fDescent - fFontMetrics.fAscent + fFontMetrics.fLeading);
   *     }
   * ```
   */
  public fun offset(): Int {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector offset() const { return fOffset; }
   * ```
   */
  public fun ascent(): Int {
    TODO("Implement ascent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar ascent() const { return fFontMetrics.fAscent + fBaselineShift; }
   * ```
   */
  public fun descent(): Int {
    TODO("Implement descent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar descent() const { return fFontMetrics.fDescent + fBaselineShift; }
   * ```
   */
  public fun leading(): Int {
    TODO("Implement leading")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar leading() const { return fFontMetrics.fLeading; }
   * ```
   */
  public fun correctAscent(): Int {
    TODO("Implement correctAscent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar correctAscent() const { return fCorrectAscent + fBaselineShift; }
   * ```
   */
  public fun correctDescent(): Int {
    TODO("Implement correctDescent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar correctDescent() const { return fCorrectDescent + fBaselineShift; }
   * ```
   */
  public fun correctLeading(): Int {
    TODO("Implement correctLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar correctLeading() const { return fCorrectLeading; }
   * ```
   */
  public fun font(): Int {
    TODO("Implement font")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFont& font() const { return fFont; }
   * ```
   */
  public fun leftToRight(): Boolean {
    TODO("Implement leftToRight")
  }

  /**
   * C++ original:
   * ```cpp
   * bool leftToRight() const { return fBidiLevel % 2 == 0; }
   * ```
   */
  public fun getTextDirection(): Int {
    TODO("Implement getTextDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * TextDirection getTextDirection() const { return leftToRight() ? TextDirection::kLtr : TextDirection::kRtl; }
   * ```
   */
  public fun index(): Int {
    TODO("Implement index")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t index() const { return fIndex; }
   * ```
   */
  public fun heightMultiplier(): Int {
    TODO("Implement heightMultiplier")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar heightMultiplier() const { return fHeightMultiplier; }
   * ```
   */
  public fun useHalfLeading(): Boolean {
    TODO("Implement useHalfLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool useHalfLeading() const { return fUseHalfLeading; }
   * ```
   */
  public fun baselineShift(): Int {
    TODO("Implement baselineShift")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar baselineShift() const { return fBaselineShift; }
   * ```
   */
  public fun placeholderStyle(): Int {
    TODO("Implement placeholderStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * PlaceholderStyle* Run::placeholderStyle() const {
   *     if (isPlaceholder()) {
   *         return &fOwner->placeholders()[fPlaceholderIndex].fStyle;
   *     } else {
   *         return nullptr;
   *     }
   * }
   * ```
   */
  public fun isPlaceholder(): Boolean {
    TODO("Implement isPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPlaceholder() const { return fPlaceholderIndex != std::numeric_limits<size_t>::max(); }
   * ```
   */
  public fun clusterIndex(pos: ULong): Int {
    TODO("Implement clusterIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t clusterIndex(size_t pos) const { return fClusterIndexes[pos]; }
   * ```
   */
  public fun globalClusterIndex(pos: ULong): Int {
    TODO("Implement globalClusterIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t globalClusterIndex(size_t pos) const { return fClusterStart + fClusterIndexes[pos]; }
   * ```
   */
  public fun positionX(pos: ULong): Int {
    TODO("Implement positionX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Run::positionX(size_t pos) const {
   *     return posX(pos) + (fJustificationShifts.empty() ? 0 : fJustificationShifts[pos].fY);
   * }
   * ```
   */
  public fun textRange(): Int {
    TODO("Implement textRange")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange textRange() const { return fTextRange; }
   * ```
   */
  public fun clusterRange(): ClusterRange {
    TODO("Implement clusterRange")
  }

  /**
   * C++ original:
   * ```cpp
   * ClusterRange clusterRange() const { return fClusterRange; }
   * ```
   */
  public fun owner(): ParagraphImpl {
    TODO("Implement owner")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphImpl* owner() const { return fOwner; }
   * ```
   */
  public fun isEllipsis(): Boolean {
    TODO("Implement isEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEllipsis() const { return fEllipsis; }
   * ```
   */
  public fun calculateMetrics() {
    TODO("Implement calculateMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::calculateMetrics() {
   *     fCorrectAscent = fFontMetrics.fAscent - fFontMetrics.fLeading * 0.5;
   *     fCorrectDescent = fFontMetrics.fDescent + fFontMetrics.fLeading * 0.5;
   *     fCorrectLeading = 0;
   *     if (SkScalarNearlyZero(fHeightMultiplier)) {
   *         return;
   *     }
   *     const auto runHeight = fHeightMultiplier * fFont.getSize();
   *     const auto fontIntrinsicHeight = fCorrectDescent - fCorrectAscent;
   *     if (fUseHalfLeading) {
   *         const auto extraLeading = (runHeight - fontIntrinsicHeight) / 2;
   *         fCorrectAscent -= extraLeading;
   *         fCorrectDescent += extraLeading;
   *     } else {
   *         const auto multiplier = runHeight / fontIntrinsicHeight;
   *         fCorrectAscent *= multiplier;
   *         fCorrectDescent *= multiplier;
   *     }
   *     // If we shift the baseline we need to make sure the shifted text fits the line
   *     fCorrectAscent += fBaselineShift;
   *     fCorrectDescent += fBaselineShift;
   * }
   * ```
   */
  public fun updateMetrics(endlineMetrics: InternalLineMetrics?) {
    TODO("Implement updateMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::updateMetrics(InternalLineMetrics* endlineMetrics) {
   *
   *     SkASSERT(isPlaceholder());
   *     auto placeholderStyle = this->placeholderStyle();
   *     // Difference between the placeholder baseline and the line bottom
   *     SkScalar baselineAdjustment = 0;
   *     switch (placeholderStyle->fBaseline) {
   *         case TextBaseline::kAlphabetic:
   *             break;
   *
   *         case TextBaseline::kIdeographic:
   *             baselineAdjustment = endlineMetrics->deltaBaselines() / 2;
   *             break;
   *     }
   *
   *     auto height = placeholderStyle->fHeight;
   *     auto offset = placeholderStyle->fBaselineOffset;
   *
   *     fFontMetrics.fLeading = 0;
   *     switch (placeholderStyle->fAlignment) {
   *         case PlaceholderAlignment::kBaseline:
   *             fFontMetrics.fAscent = baselineAdjustment - offset;
   *             fFontMetrics.fDescent = baselineAdjustment + height - offset;
   *             break;
   *
   *         case PlaceholderAlignment::kAboveBaseline:
   *             fFontMetrics.fAscent = baselineAdjustment - height;
   *             fFontMetrics.fDescent = baselineAdjustment;
   *             break;
   *
   *         case PlaceholderAlignment::kBelowBaseline:
   *             fFontMetrics.fAscent = baselineAdjustment;
   *             fFontMetrics.fDescent = baselineAdjustment + height;
   *             break;
   *
   *         case PlaceholderAlignment::kTop:
   *             fFontMetrics.fDescent = height + fFontMetrics.fAscent;
   *             break;
   *
   *         case PlaceholderAlignment::kBottom:
   *             fFontMetrics.fAscent = fFontMetrics.fDescent - height;
   *             break;
   *
   *         case PlaceholderAlignment::kMiddle:
   *             auto mid = (-fFontMetrics.fDescent - fFontMetrics.fAscent)/2.0;
   *             fFontMetrics.fDescent = height/2.0 - mid;
   *             fFontMetrics.fAscent =  - height/2.0 - mid;
   *             break;
   *     }
   *
   *     this->calculateMetrics();
   *
   *     // Make sure the placeholder can fit the line
   *     endlineMetrics->add(this);
   * }
   * ```
   */
  public fun setClusterRange(from: ULong, to: ULong) {
    TODO("Implement setClusterRange")
  }

  /**
   * C++ original:
   * ```cpp
   * void setClusterRange(size_t from, size_t to) { fClusterRange = ClusterRange(from, to); }
   * ```
   */
  public fun clip(): Int {
    TODO("Implement clip")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect clip() const {
   *         return SkRect::MakeXYWH(fOffset.fX, fOffset.fY, fAdvance.fX, fAdvance.fY);
   *     }
   * ```
   */
  public fun isCursiveScript(): Boolean {
    TODO("Implement isCursiveScript")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Run::isCursiveScript() const {
   *     switch (this->fScript) {
   *         case SkSetFourByteTag('A', 'r', 'a', 'b'): // ARABIC
   *         case SkSetFourByteTag('R', 'o', 'h', 'g'): // HANIFI_ROHINGYA
   *         case SkSetFourByteTag('M', 'a', 'n', 'd'): // MANDAIC
   *         case SkSetFourByteTag('M', 'o', 'n', 'g'): // MONGOLIAN
   *         case SkSetFourByteTag('N', 'k', 'o', 'o'): // NKO
   *         case SkSetFourByteTag('P', 'h', 'a', 'g'): // PHAGS_PA
   *         case SkSetFourByteTag('S', 'y', 'r', 'c'): // SYRIAC
   *             return true;
   *         default:
   *             return false;
   *     }
   * }
   * ```
   */
  public fun addSpacesAtTheEnd(space: SkScalar, cluster: Cluster?) {
    TODO("Implement addSpacesAtTheEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::addSpacesAtTheEnd(SkScalar space, Cluster* cluster) {
   *     // Increment the run width
   *     fAdvance.fX += space;
   *     // Increment the cluster width
   *     cluster->space(space);
   * }
   * ```
   */
  public fun addLetterSpacesEvenly(space: SkScalar, cluster: Cluster?): Int {
    TODO("Implement addLetterSpacesEvenly")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Run::addLetterSpacesEvenly(SkScalar space, Cluster* cluster) {
   *     if (this->isCursiveScript()) {
   *         // Do not apply letter spacing for script languages
   *         return 0.0;
   *     }
   *     // Offset all the glyphs in the cluster
   *     SkScalar shift = 0;
   *     for (size_t i = cluster->startPos(); i < cluster->endPos(); ++i) {
   *         fPositions[i].fX += shift;
   *         shift += space;
   *     }
   *     if (this->size() == cluster->endPos()) {
   *         // To make calculations easier
   *         fPositions[cluster->endPos()].fX += shift;
   *     }
   *     // Increment the run width
   *     fAdvance.fX += shift;
   *     // Increment the cluster width
   *     cluster->space(shift);
   *     cluster->setHalfLetterSpacing(space / 2);
   *
   *     return shift;
   * }
   * ```
   */
  public fun addLetterSpacesEvenly(space: SkScalar): Int {
    TODO("Implement addLetterSpacesEvenly")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Run::addLetterSpacesEvenly(SkScalar space) {
   *     if (this->isCursiveScript()) {
   *         // Do not apply letter spacing for script languages
   *         return 0.0;
   *     }
   *     SkScalar shift = 0;
   *     for (size_t i = 0; i < this->size(); ++i) {
   *         fPositions[i].fX += shift;
   *         shift += space;
   *     }
   *     fPositions[this->size()].fX += shift;
   *     fAdvance.fX += shift;
   *     return shift;
   * }
   * ```
   */
  public fun shift(cluster: Cluster?, offset: SkScalar) {
    TODO("Implement shift")
  }

  /**
   * C++ original:
   * ```cpp
   * void shift(const Cluster* cluster, SkScalar offset)
   * ```
   */
  public fun extend(cluster: Cluster?, offset: SkScalar) {
    TODO("Implement extend")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::extend(const Cluster* cluster, SkScalar offset) {
   *     // Extend the cluster at the end
   *     fPositions[cluster->endPos()].fX += offset;
   * }
   * ```
   */
  public fun calculateHeight(ascentStyle: LineMetricStyle, descentStyle: LineMetricStyle): Int {
    TODO("Implement calculateHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar calculateHeight(LineMetricStyle ascentStyle, LineMetricStyle descentStyle) const {
   *         auto ascent = ascentStyle == LineMetricStyle::Typographic ? this->ascent()
   *                                     : this->correctAscent();
   *         auto descent = descentStyle == LineMetricStyle::Typographic ? this->descent()
   *                                       : this->correctDescent();
   *         return descent - ascent;
   *     }
   * ```
   */
  public fun calculateWidth(
    start: ULong,
    end: ULong,
    clip: Boolean,
  ): Int {
    TODO("Implement calculateWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Run::calculateWidth(size_t start, size_t end, bool clip) const {
   *     SkASSERT(start <= end);
   *     // clip |= end == size();  // Clip at the end of the run?
   *     auto correction = 0.0f;
   *     if (end > start && !fJustificationShifts.empty()) {
   *         // This is not a typo: we are using Point as a pair of SkScalars
   *         correction = fJustificationShifts[end - 1].fX -
   *                      fJustificationShifts[start].fY;
   *     }
   *     return posX(end) - posX(start) + correction;
   * }
   * ```
   */
  public fun copyTo(
    builder: SkTextBlobBuilder,
    pos: ULong,
    size: ULong,
  ) {
    TODO("Implement copyTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::copyTo(SkTextBlobBuilder& builder, size_t pos, size_t size) const {
   *     SkASSERT(pos + size <= this->size());
   *     const auto& blobBuffer = builder.allocRunPos(fFont, SkToInt(size));
   *     sk_careful_memcpy(blobBuffer.glyphs, fGlyphs.data() + pos, size * sizeof(SkGlyphID));
   *
   *     for (size_t i = 0; i < size; ++i) {
   *         auto point = fPositions[i + pos];
   *         if (!fJustificationShifts.empty()) {
   *             point.fX += fJustificationShifts[i + pos].fX;
   *         }
   *         point += fOffsets[i + pos];
   *         blobBuffer.points()[i] = point;
   *     }
   * }
   * ```
   */
  public fun <Visitor> iterateThroughClustersInTextOrder(visitor: Visitor) {
    TODO("Implement iterateThroughClustersInTextOrder")
  }

  /**
   * C++ original:
   * ```cpp
   * template<typename Visitor>
   * void Run::iterateThroughClustersInTextOrder(Visitor visitor) {
   *     // Can't figure out how to do it with one code for both cases without 100 ifs
   *     // Can't go through clusters because there are no cluster table yet
   *     if (leftToRight()) {
   *         size_t start = 0;
   *         size_t cluster = this->clusterIndex(start);
   *         for (size_t glyph = 1; glyph <= this->size(); ++glyph) {
   *             auto nextCluster = this->clusterIndex(glyph);
   *             if (nextCluster <= cluster) {
   *                 continue;
   *             }
   *
   *             visitor(start,
   *                     glyph,
   *                     fClusterStart + cluster,
   *                     fClusterStart + nextCluster,
   *                     this->calculateWidth(start, glyph, glyph == size()),
   *                     this->calculateHeight(LineMetricStyle::CSS, LineMetricStyle::CSS));
   *
   *             start = glyph;
   *             cluster = nextCluster;
   *         }
   *     } else {
   *         size_t glyph = this->size();
   *         size_t cluster = this->fUtf8Range.begin();
   *         for (int32_t start = this->size() - 1; start >= 0; --start) {
   *             size_t nextCluster =
   *                     start == 0 ? this->fUtf8Range.end() : this->clusterIndex(start - 1);
   *             if (nextCluster <= cluster) {
   *                 continue;
   *             }
   *
   *             visitor(start,
   *                     glyph,
   *                     fClusterStart + cluster,
   *                     fClusterStart + nextCluster,
   *                     this->calculateWidth(start, glyph, glyph == 0),
   *                     this->calculateHeight(LineMetricStyle::CSS, LineMetricStyle::CSS));
   *
   *             glyph = start;
   *             cluster = nextCluster;
   *         }
   *     }
   * }
   * ```
   */
  public fun iterateThroughClusters(visitor: ClusterVisitor) {
    TODO("Implement iterateThroughClusters")
  }

  /**
   * C++ original:
   * ```cpp
   * void Run::iterateThroughClusters(const ClusterVisitor& visitor) {
   *
   *     for (size_t index = 0; index < fClusterRange.width(); ++index) {
   *         auto correctIndex = leftToRight() ? fClusterRange.start + index : fClusterRange.end - index - 1;
   *         auto cluster = &fOwner->cluster(correctIndex);
   *         visitor(cluster);
   *     }
   * }
   * ```
   */
  public fun findLimitingClusters(text: TextRange): Int {
    TODO("Implement findLimitingClusters")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, ClusterIndex, ClusterIndex> Run::findLimitingClusters(TextRange text) const {
   *     if (text.width() == 0) {
   *         // Special Flutter case for "\n" and "...\n"
   *         if (text.end > this->fTextRange.start) {
   *             ClusterIndex index = fOwner->clusterIndex(text.end - 1);
   *             return std::make_tuple(true, index, index);
   *         } else {
   *             return std::make_tuple(false, 0, 0);
   *         }
   *     }
   *
   *     ClusterRange clusterRange;
   *     bool found = true;
   *     // Deal with the case when either start or end are not align with glyph cluster edge
   *     // In such case we shift the text range to the right
   *     // (cutting from the left and adding to the right)
   *     if (leftToRight()) {
   *         // LTR: [start:end)
   *         found = clusterRange.start != fClusterRange.end;
   *         clusterRange.start = fOwner->clusterIndex(text.start);
   *         clusterRange.end = fOwner->clusterIndex(text.end - 1);
   *     } else {
   *         // RTL: (start:end]
   *         clusterRange.start = fOwner->clusterIndex(text.end);
   *         clusterRange.end = fOwner->clusterIndex(text.start + 1);
   *         found = clusterRange.end != fClusterRange.start;
   *     }
   *
   *     return std::make_tuple(
   *             found,
   *             clusterRange.start,
   *             clusterRange.end);
   * }
   * ```
   */
  public fun findLimitingGlyphClusters(text: TextRange): Int {
    TODO("Implement findLimitingGlyphClusters")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, TextIndex, TextIndex> Run::findLimitingGlyphClusters(TextRange text) const {
   *     TextIndex start = fOwner->findPreviousGlyphClusterBoundary(text.start);
   *     TextIndex end = fOwner->findNextGlyphClusterBoundary(text.end);
   *     return std::make_tuple(true, start, end);
   * }
   * ```
   */
  public fun findLimitingGraphemes(text: TextRange): Int {
    TODO("Implement findLimitingGraphemes")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, TextIndex, TextIndex> Run::findLimitingGraphemes(TextRange text) const {
   *     TextIndex start = fOwner->findPreviousGraphemeBoundary(text.start);
   *     TextIndex end = fOwner->findNextGraphemeBoundary(text.end);
   *     return std::make_tuple(true, start, end);
   * }
   * ```
   */
  public fun glyphs(): Int {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyphID> glyphs() const {
   *         return SkSpan<const SkGlyphID>(fGlyphs.begin(), fGlyphs.size());
   *     }
   * ```
   */
  public fun positions(): Int {
    TODO("Implement positions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> positions() const {
   *         return SkSpan<const SkPoint>(fPositions.begin(), fPositions.size());
   *     }
   * ```
   */
  public fun offsets(): Int {
    TODO("Implement offsets")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> offsets() const {
   *         return SkSpan<const SkPoint>(fOffsets.begin(), fOffsets.size());
   *     }
   * ```
   */
  public fun clusterIndexes(): Int {
    TODO("Implement clusterIndexes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint32_t> clusterIndexes() const {
   *         return SkSpan<const uint32_t>(fClusterIndexes.begin(), fClusterIndexes.size());
   *     }
   * ```
   */
  public fun commit() {
    TODO("Implement commit")
  }

  /**
   * C++ original:
   * ```cpp
   * void commit() { }
   * ```
   */
  public fun resetJustificationShifts() {
    TODO("Implement resetJustificationShifts")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetJustificationShifts() {
   *         fJustificationShifts.clear();
   *     }
   * ```
   */
  public fun isResolved(): Boolean {
    TODO("Implement isResolved")
  }

  public data class GlyphData public constructor(
    public var glyphs: Int,
    public var positions: Int,
    public var offsets: Int,
    public var clusterIndexes: Int,
  )
}
