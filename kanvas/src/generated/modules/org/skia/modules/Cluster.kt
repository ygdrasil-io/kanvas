package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class Cluster {
 * public:
 *     enum BreakType {
 *         None,
 *         GraphemeBreak,  // calculated for all clusters (UBRK_CHARACTER)
 *         SoftLineBreak,  // calculated for all clusters (UBRK_LINE & UBRK_CHARACTER)
 *         HardLineBreak,  // calculated for all clusters (UBRK_LINE)
 *     };
 *
 *     Cluster()
 *             : fOwner(nullptr)
 *             , fRunIndex(EMPTY_RUN)
 *             , fTextRange(EMPTY_TEXT)
 *             , fGraphemeRange(EMPTY_RANGE)
 *             , fStart(0)
 *             , fEnd()
 *             , fWidth()
 *             , fHeight()
 *             , fHalfLetterSpacing(0.0) {}
 *
 *     Cluster(ParagraphImpl* owner,
 *             RunIndex runIndex,
 *             size_t start,
 *             size_t end,
 *             SkSpan<const char> text,
 *             SkScalar width,
 *             SkScalar height);
 *
 *     explicit Cluster(TextRange textRange) : fTextRange(textRange), fGraphemeRange(EMPTY_RANGE) {}
 *
 *     Cluster(const Cluster&) = default;
 *     ~Cluster() = default;
 *
 *     SkScalar sizeToChar(TextIndex ch) const;
 *     SkScalar sizeFromChar(TextIndex ch) const;
 *
 *     size_t roundPos(SkScalar s) const;
 *
 *     void space(SkScalar shift) {
 *         fWidth += shift;
 *     }
 *
 *     ParagraphImpl* getOwner() const { return fOwner; }
 *     void setOwner(ParagraphImpl* owner) { fOwner = owner; }
 *
 *     bool isWhitespaceBreak() const { return fIsWhiteSpaceBreak; }
 *     bool isIntraWordBreak() const { return fIsIntraWordBreak; }
 *     bool isHardBreak() const { return fIsHardBreak; }
 *     bool isIdeographic() const { return fIsIdeographic; }
 *
 *     bool isSoftBreak() const;
 *     bool isGraphemeBreak() const;
 *     bool canBreakLineAfter() const { return isHardBreak() || isSoftBreak(); }
 *     size_t startPos() const { return fStart; }
 *     size_t endPos() const { return fEnd; }
 *     SkScalar width() const { return fWidth; }
 *     SkScalar height() const { return fHeight; }
 *     size_t size() const { return fEnd - fStart; }
 *
 *     void setHalfLetterSpacing(SkScalar halfLetterSpacing) { fHalfLetterSpacing = halfLetterSpacing; }
 *     SkScalar getHalfLetterSpacing() const { return fHalfLetterSpacing; }
 *
 *     TextRange textRange() const { return fTextRange; }
 *
 *     RunIndex runIndex() const { return fRunIndex; }
 *     ParagraphImpl* owner() const { return fOwner; }
 *
 *     Run* runOrNull() const;
 *     Run& run() const;
 *     SkFont font() const;
 *
 *     SkScalar trimmedWidth(size_t pos) const;
 *
 *     bool contains(TextIndex ch) const { return ch >= fTextRange.start && ch < fTextRange.end; }
 *
 *     bool belongs(TextRange text) const {
 *         return fTextRange.start >= text.start && fTextRange.end <= text.end;
 *     }
 *
 *     bool startsIn(TextRange text) const {
 *         return fTextRange.start >= text.start && fTextRange.start < text.end;
 *     }
 *
 * private:
 *
 *     friend ParagraphImpl;
 *
 *     ParagraphImpl* fOwner;
 *     RunIndex fRunIndex;
 *     TextRange fTextRange;
 *     GraphemeRange fGraphemeRange;
 *
 *     size_t fStart;
 *     size_t fEnd;
 *     SkScalar fWidth;
 *     SkScalar fHeight;
 *     SkScalar fHalfLetterSpacing;
 *
 *     bool fIsWhiteSpaceBreak;
 *     bool fIsIntraWordBreak;
 *     bool fIsHardBreak;
 *     bool fIsIdeographic;
 * }
 * ```
 */
public data class Cluster public constructor(
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
   * RunIndex fRunIndex
   * ```
   */
  private var fRunIndex: RunIndex,
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
   * GraphemeRange fGraphemeRange
   * ```
   */
  private var fGraphemeRange: GraphemeRange,
  /**
   * C++ original:
   * ```cpp
   * size_t fStart
   * ```
   */
  private var fStart: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fEnd
   * ```
   */
  private var fEnd: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWidth
   * ```
   */
  private var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  private var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHalfLetterSpacing
   * ```
   */
  private var fHalfLetterSpacing: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fIsWhiteSpaceBreak
   * ```
   */
  private var fIsWhiteSpaceBreak: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fIsIntraWordBreak
   * ```
   */
  private var fIsIntraWordBreak: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fIsHardBreak
   * ```
   */
  private var fIsHardBreak: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fIsIdeographic
   * ```
   */
  private var fIsIdeographic: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar Cluster::sizeToChar(TextIndex ch) const {
   *     if (ch < fTextRange.start || ch >= fTextRange.end) {
   *         return 0;
   *     }
   *     auto shift = ch - fTextRange.start;
   *     auto ratio = shift * 1.0 / fTextRange.width();
   *
   *     return SkDoubleToScalar(fWidth * ratio);
   * }
   * ```
   */
  public fun sizeToChar(ch: TextIndex): Int {
    TODO("Implement sizeToChar")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Cluster::sizeFromChar(TextIndex ch) const {
   *     if (ch < fTextRange.start || ch >= fTextRange.end) {
   *         return 0;
   *     }
   *     auto shift = fTextRange.end - ch - 1;
   *     auto ratio = shift * 1.0 / fTextRange.width();
   *
   *     return SkDoubleToScalar(fWidth * ratio);
   * }
   * ```
   */
  public fun sizeFromChar(ch: TextIndex): Int {
    TODO("Implement sizeFromChar")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Cluster::roundPos(SkScalar s) const {
   *     auto ratio = (s * 1.0) / fWidth;
   *     return sk_double_floor2int(ratio * size());
   * }
   * ```
   */
  public fun roundPos(s: SkScalar): Int {
    TODO("Implement roundPos")
  }

  /**
   * C++ original:
   * ```cpp
   * void space(SkScalar shift) {
   *         fWidth += shift;
   *     }
   * ```
   */
  public fun space(shift: SkScalar) {
    TODO("Implement space")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphImpl* getOwner() const { return fOwner; }
   * ```
   */
  public fun getOwner(): ParagraphImpl {
    TODO("Implement getOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOwner(ParagraphImpl* owner) { fOwner = owner; }
   * ```
   */
  public fun setOwner(owner: ParagraphImpl?) {
    TODO("Implement setOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isWhitespaceBreak() const { return fIsWhiteSpaceBreak; }
   * ```
   */
  public fun isWhitespaceBreak(): Boolean {
    TODO("Implement isWhitespaceBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isIntraWordBreak() const { return fIsIntraWordBreak; }
   * ```
   */
  public fun isIntraWordBreak(): Boolean {
    TODO("Implement isIntraWordBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHardBreak() const { return fIsHardBreak; }
   * ```
   */
  public fun isHardBreak(): Boolean {
    TODO("Implement isHardBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isIdeographic() const { return fIsIdeographic; }
   * ```
   */
  public fun isIdeographic(): Boolean {
    TODO("Implement isIdeographic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Cluster::isSoftBreak() const {
   *     return fOwner->codeUnitHasProperty(fTextRange.end,
   *                                        SkUnicode::CodeUnitFlags::kSoftLineBreakBefore);
   * }
   * ```
   */
  public fun isSoftBreak(): Boolean {
    TODO("Implement isSoftBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Cluster::isGraphemeBreak() const {
   *     return fOwner->codeUnitHasProperty(fTextRange.end, SkUnicode::CodeUnitFlags::kGraphemeStart);
   * }
   * ```
   */
  public fun isGraphemeBreak(): Boolean {
    TODO("Implement isGraphemeBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canBreakLineAfter() const { return isHardBreak() || isSoftBreak(); }
   * ```
   */
  public fun canBreakLineAfter(): Boolean {
    TODO("Implement canBreakLineAfter")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t startPos() const { return fStart; }
   * ```
   */
  public fun startPos(): Int {
    TODO("Implement startPos")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t endPos() const { return fEnd; }
   * ```
   */
  public fun endPos(): Int {
    TODO("Implement endPos")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fEnd - fStart; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHalfLetterSpacing(SkScalar halfLetterSpacing) { fHalfLetterSpacing = halfLetterSpacing; }
   * ```
   */
  public fun setHalfLetterSpacing(halfLetterSpacing: SkScalar) {
    TODO("Implement setHalfLetterSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getHalfLetterSpacing() const { return fHalfLetterSpacing; }
   * ```
   */
  public fun getHalfLetterSpacing(): Int {
    TODO("Implement getHalfLetterSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * TextRange textRange() const { return fTextRange; }
   * ```
   */
  public fun textRange(): Int {
    TODO("Implement textRange")
  }

  /**
   * C++ original:
   * ```cpp
   * RunIndex runIndex() const { return fRunIndex; }
   * ```
   */
  public fun runIndex(): RunIndex {
    TODO("Implement runIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphImpl* owner() const { return fOwner; }
   * ```
   */
  public fun owner(): ParagraphImpl {
    TODO("Implement owner")
  }

  /**
   * C++ original:
   * ```cpp
   * Run* Cluster::runOrNull() const {
   *     if (fRunIndex >= fOwner->runs().size()) {
   *         return nullptr;
   *     }
   *     return &fOwner->run(fRunIndex);
   * }
   * ```
   */
  public fun runOrNull(): Run {
    TODO("Implement runOrNull")
  }

  /**
   * C++ original:
   * ```cpp
   * Run& Cluster::run() const {
   *     SkASSERT(fRunIndex < fOwner->runs().size());
   *     return fOwner->run(fRunIndex);
   * }
   * ```
   */
  public fun run(): Run {
    TODO("Implement run")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont Cluster::font() const {
   *     SkASSERT(fRunIndex < fOwner->runs().size());
   *     return fOwner->run(fRunIndex).font();
   * }
   * ```
   */
  public fun font(): Int {
    TODO("Implement font")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar Cluster::trimmedWidth(size_t pos) const {
   *     // Find the width until the pos and return the min between trimmedWidth and the width(pos)
   *     // We don't have to take in account cluster shift since it's the same for 0 and for pos
   *     auto& run = fOwner->run(fRunIndex);
   *     return std::min(run.positionX(pos) - run.positionX(fStart), fWidth);
   * }
   * ```
   */
  public fun trimmedWidth(pos: ULong): Int {
    TODO("Implement trimmedWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(TextIndex ch) const { return ch >= fTextRange.start && ch < fTextRange.end; }
   * ```
   */
  public fun contains(ch: TextIndex): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool belongs(TextRange text) const {
   *         return fTextRange.start >= text.start && fTextRange.end <= text.end;
   *     }
   * ```
   */
  public fun belongs(text: TextRange): Boolean {
    TODO("Implement belongs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool startsIn(TextRange text) const {
   *         return fTextRange.start >= text.start && fTextRange.start < text.end;
   *     }
   * ```
   */
  public fun startsIn(text: TextRange): Boolean {
    TODO("Implement startsIn")
  }

  public enum class BreakType {
    None,
    GraphemeBreak,
    SoftLineBreak,
    HardLineBreak,
  }
}
