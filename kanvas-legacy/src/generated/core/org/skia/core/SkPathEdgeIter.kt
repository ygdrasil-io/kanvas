package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkPathEdgeIter {
 *     const SkPathVerb* fVerbs;
 *     const SkPathVerb* fVerbsStop;
 *     const SkPoint*  fPts;
 *     const SkPoint*  fMoveToPtr;
 *     const SkScalar* fConicWeights;
 *     SkPoint         fScratch[2];    // for auto-close lines
 *     bool            fNeedsCloseLine;
 *     bool            fNextIsNewContour;
 *     SkDEBUGCODE(bool fIsConic;)
 *
 * public:
 *     SkPathEdgeIter(const SkPath& path);
 *     SkPathEdgeIter(const SkPathRaw&);
 *
 *     SkScalar conicWeight() const {
 *         SkASSERT(fIsConic);
 *         return *fConicWeights;
 *     }
 *
 *     enum class Edge {
 *         kLine = (int)SkPathVerb::kLine,
 *         kQuad = (int)SkPathVerb::kQuad,
 *         kConic = (int)SkPathVerb::kConic,
 *         kCubic = (int)SkPathVerb::kCubic,
 *  //       kInvalid = 99,
 *     };
 *
 *     static SkPathVerb EdgeToVerb(Edge e) {
 *         return SkPathVerb(e);
 *     }
 *
 *     // todo: return as optional? fPts become span?
 *     struct Result {
 *         const SkPoint*  fPts;   // points for the segment, or null if done
 *         Edge            fEdge;
 *         bool            fIsNewContour;
 *
 *         // Returns true when it holds an Edge, false when the path is done.
 *         explicit operator bool() { return fPts != nullptr; }
 *     };
 *
 *     Result next() {
 *         auto closeline = [&]() {
 *             fScratch[0] = fPts[-1];
 *             fScratch[1] = *fMoveToPtr;
 *             fNeedsCloseLine = false;
 *             fNextIsNewContour = true;
 *             return Result{ fScratch, Edge::kLine, false };
 *         };
 *
 *         for (;;) {
 *             SkASSERT(fVerbs <= fVerbsStop);
 *             if (fVerbs == fVerbsStop) {
 *                 return fNeedsCloseLine ? closeline() : Result{nullptr, Edge::kLine, false};
 *             }
 *
 *             SkDEBUGCODE(fIsConic = false;)
 *
 *             const auto verb = *fVerbs++;
 *             switch (verb) {
 *                 case SkPathVerb::kMove: {
 *                     if (fNeedsCloseLine) {
 *                         auto res = closeline();
 *                         fMoveToPtr = fPts++;
 *                         return res;
 *                     }
 *                     fMoveToPtr = fPts++;
 *                     fNextIsNewContour = true;
 *                 } break;
 *                 case SkPathVerb::kClose:
 *                     if (fNeedsCloseLine) return closeline();
 *                     break;
 *                 default: {
 *                     unsigned v = static_cast<unsigned>(verb);
 *                     // Actual edge.
 *                     const int pts_count = (v+2) / 2,
 *                               cws_count = (v & (v-1)) / 2;
 *                     SkASSERT(pts_count == SkPathPriv::PtsInIter(v) - 1);
 *
 *                     fNeedsCloseLine = true;
 *                     fPts           += pts_count;
 *                     fConicWeights  += cws_count;
 *
 *                     SkDEBUGCODE(fIsConic = (verb == SkPathVerb::kConic);)
 *                     SkASSERT(fIsConic == (cws_count > 0));
 *
 *                     bool isNewContour = fNextIsNewContour;
 *                     fNextIsNewContour = false;
 *                     return { &fPts[-(pts_count + 1)], Edge(v), isNewContour };
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
public data class SkPathEdgeIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkPathVerb* fVerbs
   * ```
   */
  private val fVerbs: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * const SkPathVerb* fVerbsStop
   * ```
   */
  private val fVerbsStop: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * const SkPoint*  fPts
   * ```
   */
  private val fPts: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * const SkPoint*  fMoveToPtr
   * ```
   */
  private val fMoveToPtr: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar* fConicWeights
   * ```
   */
  private val fConicWeights: SkScalar?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint         fScratch[2]
   * ```
   */
  private var fScratch: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * bool            fNeedsCloseLine
   * ```
   */
  private var fNeedsCloseLine: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool            fNextIsNewContour
   * ```
   */
  private var fNextIsNewContour: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar conicWeight() const {
   *         SkASSERT(fIsConic);
   *         return *fConicWeights;
   *     }
   * ```
   */
  public fun conicWeight(): SkScalar {
    TODO("Implement conicWeight")
  }

  /**
   * C++ original:
   * ```cpp
   * Result next() {
   *         auto closeline = [&]() {
   *             fScratch[0] = fPts[-1];
   *             fScratch[1] = *fMoveToPtr;
   *             fNeedsCloseLine = false;
   *             fNextIsNewContour = true;
   *             return Result{ fScratch, Edge::kLine, false };
   *         };
   *
   *         for (;;) {
   *             SkASSERT(fVerbs <= fVerbsStop);
   *             if (fVerbs == fVerbsStop) {
   *                 return fNeedsCloseLine ? closeline() : Result{nullptr, Edge::kLine, false};
   *             }
   *
   *             SkDEBUGCODE(fIsConic = false;)
   *
   *             const auto verb = *fVerbs++;
   *             switch (verb) {
   *                 case SkPathVerb::kMove: {
   *                     if (fNeedsCloseLine) {
   *                         auto res = closeline();
   *                         fMoveToPtr = fPts++;
   *                         return res;
   *                     }
   *                     fMoveToPtr = fPts++;
   *                     fNextIsNewContour = true;
   *                 } break;
   *                 case SkPathVerb::kClose:
   *                     if (fNeedsCloseLine) return closeline();
   *                     break;
   *                 default: {
   *                     unsigned v = static_cast<unsigned>(verb);
   *                     // Actual edge.
   *                     const int pts_count = (v+2) / 2,
   *                               cws_count = (v & (v-1)) / 2;
   *                     SkASSERT(pts_count == SkPathPriv::PtsInIter(v) - 1);
   *
   *                     fNeedsCloseLine = true;
   *                     fPts           += pts_count;
   *                     fConicWeights  += cws_count;
   *
   *                     SkDEBUGCODE(fIsConic = (verb == SkPathVerb::kConic);)
   *                     SkASSERT(fIsConic == (cws_count > 0));
   *
   *                     bool isNewContour = fNextIsNewContour;
   *                     fNextIsNewContour = false;
   *                     return { &fPts[-(pts_count + 1)], Edge(v), isNewContour };
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  public fun next(): Result {
    TODO("Implement next")
  }

  public open class Result public constructor(
    public val fPts: SkPoint?,
    public var fEdge: org.skia.core.Edge,
    public var fIsNewContour: Boolean,
  )

  public enum class Edge {
    kLine,
    kQuad,
    kConic,
    kCubic,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkPathVerb EdgeToVerb(Edge e) {
     *         return SkPathVerb(e);
     *     }
     * ```
     */
    public fun edgeToVerb(e: Edge): SkPathVerb {
      TODO("Implement edgeToVerb")
    }
  }
}
