package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.math.SkScalar
import undefined.AddLineToParagraph

/**
 * C++ original:
 * ```cpp
 * class TextWrapper {
 *     class ClusterPos {
 *     public:
 *         ClusterPos() : fCluster(nullptr), fPos(0) {}
 *         ClusterPos(Cluster* cluster, size_t pos) : fCluster(cluster), fPos(pos) {}
 *         inline Cluster* cluster() const { return fCluster; }
 *         inline size_t position() const { return fPos; }
 *         inline void setPosition(size_t pos) { fPos = pos; }
 *         void clean() {
 *             fCluster = nullptr;
 *             fPos = 0;
 *         }
 *         void move(bool up) {
 *             fCluster += up ? 1 : -1;
 *             fPos = up ? 0 : fCluster->endPos();
 *         }
 *
 *     private:
 *         Cluster* fCluster;
 *         size_t fPos;
 *     };
 *     class TextStretch {
 *     public:
 *         TextStretch() : fStart(), fEnd(), fWidth(0), fWidthWithGhostSpaces(0) {}
 *         TextStretch(Cluster* s, Cluster* e, bool forceStrut)
 *                 : fStart(s, 0), fEnd(e, e->endPos()), fMetrics(forceStrut), fWidth(0), fWidthWithGhostSpaces(0) {
 *             for (auto c = s; c <= e; ++c) {
 *                 if (auto r = c->runOrNull()) {
 *                     fMetrics.add(r);
 *                 }
 *                 if (c < e) {
 *                     fWidth += c->width();
 *                 }
 *             }
 *             fWidthWithGhostSpaces = fWidth;
 *         }
 *
 *         inline SkScalar width() const { return fWidth; }
 *         SkScalar widthWithGhostSpaces() const { return fWidthWithGhostSpaces; }
 *         inline Cluster* startCluster() const { return fStart.cluster(); }
 *         inline Cluster* endCluster() const { return fEnd.cluster(); }
 *         inline Cluster* breakCluster() const { return fBreak.cluster(); }
 *         inline InternalLineMetrics& metrics() { return fMetrics; }
 *         inline size_t startPos() const { return fStart.position(); }
 *         inline size_t endPos() const { return fEnd.position(); }
 *         bool endOfCluster() { return fEnd.position() == fEnd.cluster()->endPos(); }
 *         bool endOfWord() {
 *             return endOfCluster() &&
 *                    (fEnd.cluster()->isHardBreak() || fEnd.cluster()->isSoftBreak());
 *         }
 *
 *         void extend(TextStretch& stretch) {
 *             fMetrics.add(stretch.fMetrics);
 *             fEnd = stretch.fEnd;
 *             fWidth += stretch.fWidth;
 *             stretch.clean();
 *         }
 *
 *         bool empty() { return fStart.cluster() == fEnd.cluster() &&
 *                               fStart.position() == fEnd.position(); }
 *
 *         void setMetrics(const InternalLineMetrics& metrics) { fMetrics = metrics; }
 *
 *         void extend(Cluster* cluster) {
 *             if (fStart.cluster() == nullptr) {
 *                 fStart = ClusterPos(cluster, cluster->startPos());
 *             }
 *             fEnd = ClusterPos(cluster, cluster->endPos());
 *             // TODO: Make sure all the checks are correct and there are no unnecessary checks
 *             auto& r = cluster->run();
 *             if (!cluster->isHardBreak() && !r.isPlaceholder()) {
 *                 // We ignore metrics for \n as the Flutter does
 *                 fMetrics.add(&r);
 *             }
 *             fWidth += cluster->width();
 *         }
 *
 *         void extend(Cluster* cluster, size_t pos) {
 *             fEnd = ClusterPos(cluster, pos);
 *             if (auto r = cluster->runOrNull()) {
 *                 fMetrics.add(r);
 *             }
 *         }
 *
 *         void startFrom(Cluster* cluster, size_t pos) {
 *             fStart = ClusterPos(cluster, pos);
 *             fEnd = ClusterPos(cluster, pos);
 *             if (auto r = cluster->runOrNull()) {
 *                 // In case of placeholder we should ignore the default text style -
 *                 // we will pick up the correct one from the placeholder
 *                 if (!r->isPlaceholder()) {
 *                     fMetrics.add(r);
 *                 }
 *             }
 *             fWidth = 0;
 *         }
 *
 *         void saveBreak() {
 *             fWidthWithGhostSpaces = fWidth;
 *             fBreak = fEnd;
 *         }
 *
 *         void restoreBreak() {
 *             fWidth = fWidthWithGhostSpaces;
 *             fEnd = fBreak;
 *         }
 *
 *         void shiftBreak() {
 *             fBreak.move(true);
 *         }
 *
 *         void trim() {
 *
 *             if (fEnd.cluster() != nullptr &&
 *                 fEnd.cluster()->owner() != nullptr &&
 *                 fEnd.cluster()->runOrNull() != nullptr &&
 *                 fEnd.cluster()->run().placeholderStyle() == nullptr &&
 *                 fWidth > 0) {
 *                 fWidth -= (fEnd.cluster()->width() - fEnd.cluster()->trimmedWidth(fEnd.position()));
 *             }
 *         }
 *
 *         void trim(Cluster* cluster) {
 *             SkASSERT(fEnd.cluster() == cluster);
 *             if (fEnd.cluster() > fStart.cluster()) {
 *                 fEnd.move(false);
 *                 fWidth -= cluster->width();
 *             } else {
 *                 fEnd.setPosition(fStart.position());
 *                 fWidth = 0;
 *             }
 *         }
 *
 *         void clean() {
 *             fStart.clean();
 *             fEnd.clean();
 *             fWidth = 0;
 *             fMetrics.clean();
 *         }
 *
 *     private:
 *         ClusterPos fStart;
 *         ClusterPos fEnd;
 *         ClusterPos fBreak;
 *         InternalLineMetrics fMetrics;
 *         SkScalar fWidth;
 *         SkScalar fWidthWithGhostSpaces;
 *     };
 *
 * public:
 *     TextWrapper() {
 *          fLineNumber = 1;
 *          fHardLineBreak = false;
 *          fExceededMaxLines = false;
 *     }
 *
 *     using AddLineToParagraph = std::function<void(TextRange textExcludingSpaces,
 *                                                   TextRange text,
 *                                                   TextRange textIncludingNewlines,
 *                                                   ClusterRange clusters,
 *                                                   ClusterRange clustersWithGhosts,
 *                                                   SkScalar AddLineToParagraph,
 *                                                   size_t startClip,
 *                                                   size_t endClip,
 *                                                   SkVector offset,
 *                                                   SkVector advance,
 *                                                   InternalLineMetrics metrics,
 *                                                   bool addEllipsis)>;
 *     void breakTextIntoLines(ParagraphImpl* parent,
 *                             SkScalar maxWidth,
 *                             const AddLineToParagraph& addLine);
 *
 *     SkScalar height() const { return fHeight; }
 *     SkScalar minIntrinsicWidth() const { return fMinIntrinsicWidth; }
 *     SkScalar maxIntrinsicWidth() const { return fMaxIntrinsicWidth; }
 *     bool exceededMaxLines() const { return fExceededMaxLines; }
 *
 * private:
 *     TextStretch fWords;
 *     TextStretch fClusters;
 *     TextStretch fClip;
 *     TextStretch fEndLine;
 *     size_t fLineNumber;
 *     bool fTooLongWord;
 *     bool fTooLongCluster;
 *
 *     bool fHardLineBreak;
 *     bool fExceededMaxLines;
 *
 *     SkScalar fHeight;
 *     SkScalar fMinIntrinsicWidth;
 *     SkScalar fMaxIntrinsicWidth;
 *
 *     void reset() {
 *         fWords.clean();
 *         fClusters.clean();
 *         fClip.clean();
 *         fTooLongCluster = false;
 *         fTooLongWord = false;
 *         fHardLineBreak = false;
 *     }
 *
 *     void lookAhead(SkScalar maxWidth, Cluster* endOfClusters, bool applyRoundingHack);
 *     void moveForward(bool hasEllipsis);
 *     void trimEndSpaces(TextAlign align);
 *     std::tuple<Cluster*, size_t, SkScalar> trimStartSpaces(Cluster* endOfClusters);
 *     SkScalar getClustersTrimmedWidth();
 * }
 * ```
 */
public data class TextWrapper public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextStretch fWords
   * ```
   */
  private var fWords: TextStretch,
  /**
   * C++ original:
   * ```cpp
   * TextStretch fClusters
   * ```
   */
  private var fClusters: TextStretch,
  /**
   * C++ original:
   * ```cpp
   * TextStretch fClip
   * ```
   */
  private var fClip: TextStretch,
  /**
   * C++ original:
   * ```cpp
   * TextStretch fEndLine
   * ```
   */
  private var fEndLine: TextStretch,
  /**
   * C++ original:
   * ```cpp
   * size_t fLineNumber
   * ```
   */
  private var fLineNumber: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fTooLongWord
   * ```
   */
  private var fTooLongWord: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fTooLongCluster
   * ```
   */
  private var fTooLongCluster: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHardLineBreak
   * ```
   */
  private var fHardLineBreak: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fExceededMaxLines
   * ```
   */
  private var fExceededMaxLines: Boolean,
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
   * SkScalar fMinIntrinsicWidth
   * ```
   */
  private var fMinIntrinsicWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fMaxIntrinsicWidth
   * ```
   */
  private var fMaxIntrinsicWidth: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void TextWrapper::breakTextIntoLines(ParagraphImpl* parent,
   *                                      SkScalar maxWidth,
   *                                      const AddLineToParagraph& addLine) {
   *     fHeight = 0;
   *     fMinIntrinsicWidth = std::numeric_limits<SkScalar>::min();
   *     fMaxIntrinsicWidth = std::numeric_limits<SkScalar>::min();
   *
   *     auto span = parent->clusters();
   *     if (span.empty()) {
   *         return;
   *     }
   *     auto maxLines = parent->paragraphStyle().getMaxLines();
   *     auto align = parent->paragraphStyle().effective_align();
   *     auto unlimitedLines = maxLines == std::numeric_limits<size_t>::max();
   *     auto endlessLine = !SkIsFinite(maxWidth);
   *     auto hasEllipsis = parent->paragraphStyle().ellipsized();
   *
   *     auto disableFirstAscent = parent->paragraphStyle().getTextHeightBehavior() & TextHeightBehavior::kDisableFirstAscent;
   *     auto disableLastDescent = parent->paragraphStyle().getTextHeightBehavior() & TextHeightBehavior::kDisableLastDescent;
   *     bool firstLine = true; // We only interested in fist line if we have to disable the first ascent
   *
   *     SkScalar softLineMaxIntrinsicWidth = 0;
   *     fEndLine = TextStretch(span.data(), span.data(), parent->strutForceHeight());
   *     auto start = span.data();
   *     auto end = start + span.size() - 1;
   *     InternalLineMetrics maxRunMetrics;
   *     bool needEllipsis = false;
   *     while (fEndLine.endCluster() != end) {
   *
   *         this->lookAhead(maxWidth, end, parent->getApplyRoundingHack());
   *
   *         auto lastLine = (hasEllipsis && unlimitedLines) || fLineNumber >= maxLines;
   *         needEllipsis = hasEllipsis && !endlessLine && lastLine;
   *
   *         this->moveForward(needEllipsis);
   *         needEllipsis &= fEndLine.endCluster() < end - 1; // Only if we have some text to ellipsize
   *
   *         // Do not trim end spaces on the naturally last line of the left aligned text
   *         this->trimEndSpaces(align);
   *
   *         // For soft line breaks add to the line all the spaces next to it
   *         Cluster* startLine;
   *         size_t pos;
   *         SkScalar widthWithSpaces;
   *         std::tie(startLine, pos, widthWithSpaces) = this->trimStartSpaces(end);
   *
   *         if (needEllipsis && !fHardLineBreak) {
   *             // This is what we need to do to preserve a space before the ellipsis
   *             fEndLine.restoreBreak();
   *             widthWithSpaces = fEndLine.widthWithGhostSpaces();
   *         }
   *
   *         // If the line is empty with the hard line break, let's take the paragraph font (flutter???)
   *         if (fEndLine.metrics().isClean()) {
   *             fEndLine.setMetrics(parent->getEmptyMetrics());
   *         }
   *
   *         // Deal with placeholder clusters == runs[@size==1]
   *         Run* lastRun = nullptr;
   *         for (auto cluster = fEndLine.startCluster(); cluster <= fEndLine.endCluster(); ++cluster) {
   *             auto r = cluster->runOrNull();
   *             if (r == lastRun) {
   *                 continue;
   *             }
   *             lastRun = r;
   *             if (lastRun->placeholderStyle() != nullptr) {
   *                 SkASSERT(lastRun->size() == 1);
   *                 // Update the placeholder metrics so we can get the placeholder positions later
   *                 // and the line metrics (to make sure the placeholder fits)
   *                 lastRun->updateMetrics(&fEndLine.metrics());
   *             }
   *         }
   *
   *         // Before we update the line metrics with struts,
   *         // let's save it for GetRectsForRange(RectHeightStyle::kMax)
   *         maxRunMetrics = fEndLine.metrics();
   *         maxRunMetrics.fForceStrut = false;
   *
   *         // TODO: keep start/end/break info for text and runs but in a better way that below
   *         TextRange textExcludingSpaces(fEndLine.startCluster()->textRange().start, fEndLine.endCluster()->textRange().end);
   *         TextRange text(fEndLine.startCluster()->textRange().start, fEndLine.breakCluster()->textRange().start);
   *         TextRange textIncludingNewlines(fEndLine.startCluster()->textRange().start, startLine->textRange().start);
   *         if (startLine == end) {
   *             textIncludingNewlines.end = parent->text().size();
   *             text.end = parent->text().size();
   *         }
   *         ClusterRange clusters(fEndLine.startCluster() - start, fEndLine.endCluster() - start + 1);
   *         ClusterRange clustersWithGhosts(fEndLine.startCluster() - start, startLine - start);
   *
   *         if (disableFirstAscent && firstLine) {
   *             fEndLine.metrics().fAscent = fEndLine.metrics().fRawAscent;
   *         }
   *         if (disableLastDescent && (lastLine || (startLine == end && !fHardLineBreak ))) {
   *             fEndLine.metrics().fDescent = fEndLine.metrics().fRawDescent;
   *         }
   *
   *         if (parent->strutEnabled()) {
   *             // Make sure font metrics are not less than the strut
   *             parent->strutMetrics().updateLineMetrics(fEndLine.metrics());
   *         }
   *
   *         SkScalar lineHeight = fEndLine.metrics().height();
   *         firstLine = false;
   *
   *         if (fEndLine.empty()) {
   *             // Correct text and clusters (make it empty for an empty line)
   *             textExcludingSpaces.end = textExcludingSpaces.start;
   *             clusters.end = clusters.start;
   *         }
   *
   *         // In case of a force wrapping we don't have a break cluster and have to use the end cluster
   *         text.end = std::max(text.end, textExcludingSpaces.end);
   *
   *         addLine(textExcludingSpaces,
   *                 text,
   *                 textIncludingNewlines, clusters, clustersWithGhosts, widthWithSpaces,
   *                 fEndLine.startPos(),
   *                 fEndLine.endPos(),
   *                 SkVector::Make(0, fHeight),
   *                 SkVector::Make(fEndLine.width(), lineHeight),
   *                 fEndLine.metrics(),
   *                 needEllipsis && !fHardLineBreak);
   *
   *         softLineMaxIntrinsicWidth += widthWithSpaces;
   *
   *         fMaxIntrinsicWidth = std::max(fMaxIntrinsicWidth, softLineMaxIntrinsicWidth);
   *         if (fHardLineBreak) {
   *             softLineMaxIntrinsicWidth = 0;
   *         }
   *         // Start a new line
   *         fHeight += lineHeight;
   *         if (!fHardLineBreak || startLine != end) {
   *             fEndLine.clean();
   *         }
   *         fEndLine.startFrom(startLine, pos);
   *         parent->fMaxWidthWithTrailingSpaces = std::max(parent->fMaxWidthWithTrailingSpaces, widthWithSpaces);
   *
   *         if (hasEllipsis && unlimitedLines) {
   *             // There is one case when we need an ellipsis on a separate line
   *             // after a line break when width is infinite
   *             if (!fHardLineBreak) {
   *                 break;
   *             }
   *         } else if (lastLine) {
   *             // There is nothing more to draw
   *             fHardLineBreak = false;
   *             break;
   *         }
   *
   *         ++fLineNumber;
   *     }
   *
   *     // We finished formatting the text but we need to scan the rest for some numbers
   *     // TODO: make it a case of a normal flow
   *     if (fEndLine.endCluster() != nullptr) {
   *         auto lastWordLength = 0.0f;
   *         auto cluster = fEndLine.endCluster();
   *         while (cluster != end || cluster->endPos() < end->endPos()) {
   *             fExceededMaxLines = true;
   *             if (cluster->isHardBreak()) {
   *                 // Hard line break ends the word and the line
   *                 fMaxIntrinsicWidth = std::max(fMaxIntrinsicWidth, softLineMaxIntrinsicWidth);
   *                 softLineMaxIntrinsicWidth = 0;
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, lastWordLength);
   *                 lastWordLength = 0;
   *             } else if (cluster->isWhitespaceBreak()) {
   *                 // Whitespaces end the word
   *                 softLineMaxIntrinsicWidth += cluster->width();
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, lastWordLength);
   *                 lastWordLength = 0;
   *             } else if (cluster->run().isPlaceholder()) {
   *                 // Placeholder ends the previous word and creates a separate one
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, lastWordLength);
   *                 // Placeholder width now counts in fMinIntrinsicWidth
   *                 softLineMaxIntrinsicWidth += cluster->width();
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, cluster->width());
   *                 lastWordLength = 0;
   *             } else {
   *                 // Nothing out of ordinary - just add this cluster to the word and to the line
   *                 softLineMaxIntrinsicWidth += cluster->width();
   *                 lastWordLength += cluster->width();
   *             }
   *             ++cluster;
   *         }
   *         fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, lastWordLength);
   *         fMaxIntrinsicWidth = std::max(fMaxIntrinsicWidth, softLineMaxIntrinsicWidth);
   *
   *         if (parent->lines().empty()) {
   *             // In case we could not place even a single cluster on the line
   *             if (disableFirstAscent) {
   *                 fEndLine.metrics().fAscent = fEndLine.metrics().fRawAscent;
   *             }
   *             if (disableLastDescent && !fHardLineBreak) {
   *                 fEndLine.metrics().fDescent = fEndLine.metrics().fRawDescent;
   *             }
   *             fHeight = std::max(fHeight, fEndLine.metrics().height());
   *         }
   *     }
   *
   *     if (fHardLineBreak) {
   *         if (disableLastDescent) {
   *             fEndLine.metrics().fDescent = fEndLine.metrics().fRawDescent;
   *         }
   *
   *         // Last character is a line break
   *         if (parent->strutEnabled()) {
   *             // Make sure font metrics are not less than the strut
   *             parent->strutMetrics().updateLineMetrics(fEndLine.metrics());
   *         }
   *
   *         ClusterRange clusters(fEndLine.breakCluster() - start, fEndLine.endCluster() - start);
   *         addLine(fEndLine.breakCluster()->textRange(),
   *                 fEndLine.breakCluster()->textRange(),
   *                 fEndLine.endCluster()->textRange(),
   *                 clusters,
   *                 clusters,
   *                 0,
   *                 0,
   *                 0,
   *                 SkVector::Make(0, fHeight),
   *                 SkVector::Make(0, fEndLine.metrics().height()),
   *                 fEndLine.metrics(),
   *                 needEllipsis);
   *         fHeight += fEndLine.metrics().height();
   *         parent->lines().back().setMaxRunMetrics(maxRunMetrics);
   *     }
   *
   *     if (parent->lines().empty()) {
   *         return;
   *     }
   *     // Correct line metric styles for the first and for the last lines if needed
   *     if (disableFirstAscent) {
   *         parent->lines().front().setAscentStyle(LineMetricStyle::Typographic);
   *     }
   *     if (disableLastDescent) {
   *         parent->lines().back().setDescentStyle(LineMetricStyle::Typographic);
   *     }
   * }
   * ```
   */
  public fun breakTextIntoLines(
    parent: ParagraphImpl?,
    maxWidth: SkScalar,
    addLine: AddLineToParagraph,
  ) {
    TODO("Implement breakTextIntoLines")
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
   * SkScalar minIntrinsicWidth() const { return fMinIntrinsicWidth; }
   * ```
   */
  public fun minIntrinsicWidth(): Int {
    TODO("Implement minIntrinsicWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar maxIntrinsicWidth() const { return fMaxIntrinsicWidth; }
   * ```
   */
  public fun maxIntrinsicWidth(): Int {
    TODO("Implement maxIntrinsicWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * bool exceededMaxLines() const { return fExceededMaxLines; }
   * ```
   */
  public fun exceededMaxLines(): Boolean {
    TODO("Implement exceededMaxLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fWords.clean();
   *         fClusters.clean();
   *         fClip.clean();
   *         fTooLongCluster = false;
   *         fTooLongWord = false;
   *         fHardLineBreak = false;
   *     }
   * ```
   */
  private fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextWrapper::lookAhead(SkScalar maxWidth, Cluster* endOfClusters, bool applyRoundingHack) {
   *
   *     reset();
   *     fEndLine.metrics().clean();
   *     fWords.startFrom(fEndLine.startCluster(), fEndLine.startPos());
   *     fClusters.startFrom(fEndLine.startCluster(), fEndLine.startPos());
   *     fClip.startFrom(fEndLine.startCluster(), fEndLine.startPos());
   *
   *     LineBreakerWithLittleRounding breaker(maxWidth, applyRoundingHack);
   *     Cluster* nextNonBreakingSpace = nullptr;
   *     for (auto cluster = fEndLine.endCluster(); cluster < endOfClusters; ++cluster) {
   *         if (cluster->isHardBreak()) {
   *         } else if (
   *                 // TODO: Trying to deal with flutter rounding problem. Must be removed...
   *                 SkScalar width = fWords.width() + fClusters.width() + cluster->width();
   *                 breaker.breakLine(width)) {
   *             if (cluster->isWhitespaceBreak()) {
   *                 // It's the end of the word
   *                 fClusters.extend(cluster);
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, this->getClustersTrimmedWidth());
   *                 fWords.extend(fClusters);
   *                 continue;
   *             } else if (cluster->run().isPlaceholder()) {
   *                 if (!fClusters.empty()) {
   *                     // Placeholder ends the previous word
   *                     fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, this->getClustersTrimmedWidth());
   *                     fWords.extend(fClusters);
   *                 }
   *
   *                 if (cluster->width() > maxWidth && fWords.empty()) {
   *                     // Placeholder is the only text and it's longer than the line;
   *                     // it does not count in fMinIntrinsicWidth
   *                     fClusters.extend(cluster);
   *                     fTooLongCluster = true;
   *                     fTooLongWord = true;
   *                 } else {
   *                     // Placeholder does not fit the line; it will be considered again on the next line
   *                 }
   *                 break;
   *             }
   *
   *             // Walk further to see if there is a too long word, cluster or glyph
   *             SkScalar nextWordLength = fClusters.width();
   *             SkScalar nextShortWordLength = nextWordLength;
   *             for (auto further = cluster; further != endOfClusters; ++further) {
   *                 if (further->isSoftBreak() || further->isHardBreak() || further->isWhitespaceBreak()) {
   *                     break;
   *                 }
   *                 if (further->run().isPlaceholder()) {
   *                   // Placeholder ends the word
   *                   break;
   *                 }
   *
   *                 if (nextWordLength > 0 && nextWordLength <= maxWidth && further->isIntraWordBreak()) {
   *                     // The cluster is spaces but not the end of the word in a normal sense
   *                     nextNonBreakingSpace = further;
   *                     nextShortWordLength = nextWordLength;
   *                 }
   *
   *                 if (maxWidth == 0) {
   *                     // This is a tricky flutter case: layout(width:0) places 1 cluster on each line
   *                     nextWordLength = std::max(nextWordLength, further->width());
   *                 } else {
   *                     nextWordLength += further->width();
   *                 }
   *             }
   *             if (nextWordLength > maxWidth) {
   *                 if (nextNonBreakingSpace != nullptr) {
   *                     // We only get here if the non-breaking space improves our situation
   *                     // (allows us to break the text to fit the word)
   *                     if (SkScalar shortLength = fWords.width() + nextShortWordLength;
   *                         !breaker.breakLine(shortLength)) {
   *                         // We can add the short word to the existing line
   *                         fClusters = TextStretch(fClusters.startCluster(), nextNonBreakingSpace, fClusters.metrics().getForceStrut());
   *                         fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, nextShortWordLength);
   *                         fWords.extend(fClusters);
   *                     } else {
   *                         // We can place the short word on the next line
   *                         fClusters.clean();
   *                     }
   *                     // Either way we are not in "word is too long" situation anymore
   *                     break;
   *                 }
   *                 // If the word is too long we can break it right now and hope it's enough
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, nextWordLength);
   *                 if (fClusters.endPos() - fClusters.startPos() > 1 ||
   *                     fWords.empty()) {
   *                     fTooLongWord = true;
   *                 } else {
   *                     // Even if the word is too long there is a very little space on this line.
   *                     // let's deal with it on the next line.
   *                 }
   *             }
   *
   *             if (cluster->width() > maxWidth) {
   *                 fClusters.extend(cluster);
   *                 fTooLongCluster = true;
   *                 fTooLongWord = true;
   *             }
   *             break;
   *         }
   *
   *         if (cluster->run().isPlaceholder()) {
   *             if (!fClusters.empty()) {
   *                 // Placeholder ends the previous word (placeholders are ignored in trimming)
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, getClustersTrimmedWidth());
   *                 fWords.extend(fClusters);
   *             }
   *
   *             // Placeholder is separate word and its width now is counted in minIntrinsicWidth
   *             fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, cluster->width());
   *             fWords.extend(cluster);
   *         } else {
   *             fClusters.extend(cluster);
   *
   *             // Keep adding clusters/words
   *             if (fClusters.endOfWord()) {
   *                 fMinIntrinsicWidth = std::max(fMinIntrinsicWidth, getClustersTrimmedWidth());
   *                 fWords.extend(fClusters);
   *             }
   *         }
   *
   *         if ((fHardLineBreak = cluster->isHardBreak())) {
   *             // Stop at the hard line break
   *             break;
   *         }
   *     }
   * }
   * ```
   */
  private fun lookAhead(
    maxWidth: SkScalar,
    endOfClusters: Cluster?,
    applyRoundingHack: Boolean,
  ) {
    TODO("Implement lookAhead")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextWrapper::moveForward(bool hasEllipsis) {
   *
   *     // We normally break lines by words.
   *     // The only way we may go to clusters is if the word is too long or
   *     // it's the first word and it has an ellipsis attached to it.
   *     // If nothing fits we show the clipping.
   *     if (!fWords.empty()) {
   *         fEndLine.extend(fWords);
   * #ifdef SK_IGNORE_SKPARAGRAPH_ELLIPSIS_FIX
   *         if (!fTooLongWord || hasEllipsis) { // Ellipsis added to a word
   * #else
   *         if (!fTooLongWord && !hasEllipsis) { // Ellipsis added to a grapheme
   * #endif
   *             return;
   *         }
   *     }
   *     if (!fClusters.empty()) {
   *         fEndLine.extend(fClusters);
   *         if (!fTooLongCluster) {
   *             return;
   *         }
   *     }
   *
   *     if (!fClip.empty()) {
   *         // Flutter: forget the clipped cluster but keep the metrics
   *         fEndLine.metrics().add(fClip.metrics());
   *     }
   * }
   * ```
   */
  private fun moveForward(hasEllipsis: Boolean) {
    TODO("Implement moveForward")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextWrapper::trimEndSpaces(TextAlign align) {
   *     // Remember the breaking position
   *     fEndLine.saveBreak();
   *     // Skip all space cluster at the end
   *     for (auto cluster = fEndLine.endCluster();
   *          cluster >= fEndLine.startCluster() && cluster->isWhitespaceBreak();
   *          --cluster) {
   *         fEndLine.trim(cluster);
   *     }
   *     fEndLine.trim();
   * }
   * ```
   */
  private fun trimEndSpaces(align: TextAlign) {
    TODO("Implement trimEndSpaces")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<Cluster*, size_t, SkScalar> TextWrapper::trimStartSpaces(Cluster* endOfClusters) {
   *
   *     if (fHardLineBreak) {
   *         // End of line is always end of cluster, but need to skip \n
   *         auto width = fEndLine.width();
   *         auto cluster = fEndLine.endCluster() + 1;
   *         while (cluster < fEndLine.breakCluster() && cluster->isWhitespaceBreak())  {
   *             width += cluster->width();
   *             ++cluster;
   *         }
   *         return std::make_tuple(fEndLine.breakCluster() + 1, 0, width);
   *     }
   *
   *     // breakCluster points to the end of the line;
   *     // It's a soft line break so we need to move lineStart forward skipping all the spaces
   *     auto width = fEndLine.widthWithGhostSpaces();
   *     auto cluster = fEndLine.breakCluster() + 1;
   *     while (cluster < endOfClusters && cluster->isWhitespaceBreak()) {
   *         width += cluster->width();
   *         ++cluster;
   *     }
   *
   *     if (fEndLine.breakCluster()->isWhitespaceBreak() && fEndLine.breakCluster() < endOfClusters) {
   *         // In case of a soft line break by the whitespace
   *         // fBreak should point to the beginning of the next line
   *         // (it only matters when there are trailing spaces)
   *         fEndLine.shiftBreak();
   *     }
   *
   *     return std::make_tuple(cluster, 0, width);
   * }
   * ```
   */
  private fun trimStartSpaces(endOfClusters: Cluster?): Int {
    TODO("Implement trimStartSpaces")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar TextWrapper::getClustersTrimmedWidth() {
   *     // Move the end of the line to the left
   *     SkScalar width = 0;
   *     bool trailingSpaces = true;
   *     for (auto cluster = fClusters.endCluster(); cluster >= fClusters.startCluster(); --cluster) {
   *         if (cluster->run().isPlaceholder()) {
   *             continue;
   *         }
   *         if (trailingSpaces) {
   *             if (!cluster->isWhitespaceBreak()) {
   *                 width += cluster->trimmedWidth(cluster->endPos());
   *                 trailingSpaces = false;
   *             }
   *             continue;
   *         }
   *         width += cluster->width();
   *     }
   *     return width;
   * }
   * ```
   */
  private fun getClustersTrimmedWidth(): Int {
    TODO("Implement getClustersTrimmedWidth")
  }

  public data class ClusterPos public constructor(
    private var fCluster: Int?,
    private var fPos: Int,
  ) {
    public fun cluster(): Int {
      TODO("Implement cluster")
    }

    public fun position(): Int {
      TODO("Implement position")
    }

    public fun setPosition(pos: ULong) {
      TODO("Implement setPosition")
    }

    public fun clean() {
      TODO("Implement clean")
    }

    public fun move(up: Boolean) {
      TODO("Implement move")
    }
  }

  public data class TextStretch public constructor(
    private var fStart: undefined.ClusterPos,
    private var fEnd: undefined.ClusterPos,
    private var fBreak: undefined.ClusterPos,
    private var fMetrics: Int,
    private var fWidth: Int,
    private var fWidthWithGhostSpaces: Int,
  ) {
    public fun width(): Int {
      TODO("Implement width")
    }

    public fun widthWithGhostSpaces(): Int {
      TODO("Implement widthWithGhostSpaces")
    }

    public fun startCluster(): Int {
      TODO("Implement startCluster")
    }

    public fun endCluster(): Int {
      TODO("Implement endCluster")
    }

    public fun breakCluster(): Int {
      TODO("Implement breakCluster")
    }

    public fun metrics(): Int {
      TODO("Implement metrics")
    }

    public fun startPos(): Int {
      TODO("Implement startPos")
    }

    public fun endPos(): Int {
      TODO("Implement endPos")
    }

    public fun endOfCluster(): Boolean {
      TODO("Implement endOfCluster")
    }

    public fun endOfWord(): Boolean {
      TODO("Implement endOfWord")
    }

    public fun extend(stretch: undefined.TextStretch) {
      TODO("Implement extend")
    }

    public fun empty(): Boolean {
      TODO("Implement empty")
    }

    public fun setMetrics(metrics: InternalLineMetrics) {
      TODO("Implement setMetrics")
    }

    public fun extend(cluster: Cluster?) {
      TODO("Implement extend")
    }

    public fun extend(cluster: Cluster?, pos: ULong) {
      TODO("Implement extend")
    }

    public fun startFrom(cluster: Cluster?, pos: ULong) {
      TODO("Implement startFrom")
    }

    public fun saveBreak() {
      TODO("Implement saveBreak")
    }

    public fun restoreBreak() {
      TODO("Implement restoreBreak")
    }

    public fun shiftBreak() {
      TODO("Implement shiftBreak")
    }

    public fun trim() {
      TODO("Implement trim")
    }

    public fun trim(cluster: Cluster?) {
      TODO("Implement trim")
    }

    public fun clean() {
      TODO("Implement clean")
    }
  }
}
