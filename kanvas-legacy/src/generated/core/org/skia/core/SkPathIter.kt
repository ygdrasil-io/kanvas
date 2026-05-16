package org.skia.core

import kotlin.Float
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPathIter {
 * public:
 *     struct Rec {
 *         SkSpan<const SkPoint> fPoints;
 *         float                 fConicWeight;
 *         SkPathVerb            fVerb;
 *
 *         float conicWeight() const {
 *             SkASSERT(fVerb == SkPathVerb::kConic);
 *             return fConicWeight;
 *         }
 *     };
 *
 *     SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
 *         : pIndex(0), vIndex(0), cIndex(0)
 *         , fPoints(pts), fVerbs(vbs), fConics(cns)
 *     {
 *         // For compat older iterators, we trim off a trailing Move.
 *         // SkPathData is defined to never create this pattern, so perhaps in the future
 *         // this check can be removed (or replaced by an assert)
 *         if (!vbs.empty() && vbs.back() == SkPathVerb::kMove) {
 *             fVerbs = vbs.first(vbs.size() - 1);
 *         }
 *     }
 *
 *     /*  Holds the current verb, and its associated points
 *      *  move:  pts[0]
 *      *  line:  pts[0..1]
 *      *  quad:  pts[0..2]
 *      *  conic: pts[0..2] fConicWeight
 *      *  cubic: pts[0..3]
 *      *  close: pts[0..1] ... as if close were a line from pts[0] to pts[1]
 *      */
 *     std::optional<Rec> next();
 *
 *     std::optional<SkPathVerb> peekNextVerb() const {
 *         if (vIndex < fVerbs.size()) {
 *             return fVerbs[vIndex];
 *         }
 *         return {};
 *     }
 *
 * private:
 *     size_t                   pIndex, vIndex, cIndex;
 *     SkSpan<const SkPoint>    fPoints;
 *     SkSpan<const SkPathVerb> fVerbs;
 *     SkSpan<const float>      fConics;
 *     std::array<SkPoint, 2>   fClosePointStorage;
 * }
 * ```
 */
public data class SkPathIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   * ```
   */
  private var pIndex: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t                   pIndex
   * ```
   */
  private var fClosePointStorage: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   *         : pIndex(0), vIndex(0)
   * ```
   */
  public fun vIndex(): SkPathIter {
    TODO("Implement vIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   *         : pIndex(0), vIndex(0), cIndex(0)
   * ```
   */
  public fun cIndex(): SkPathIter {
    TODO("Implement cIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   *         : pIndex(0), vIndex(0), cIndex(0)
   *         , fPoints(pts)
   * ```
   */
  public fun fPoints(param0: Int): SkPathIter {
    TODO("Implement fPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   *         : pIndex(0), vIndex(0), cIndex(0)
   *         , fPoints(pts), fVerbs(vbs)
   * ```
   */
  public fun fVerbs(param0: Int): SkPathIter {
    TODO("Implement fVerbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs, SkSpan<const float> cns)
   *         : pIndex(0), vIndex(0), cIndex(0)
   *         , fPoints(pts), fVerbs(vbs), fConics(cns)
   * ```
   */
  public fun fConics(param0: Int): SkPathIter {
    TODO("Implement fConics")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathVerb> peekNextVerb() const {
   *         if (vIndex < fVerbs.size()) {
   *             return fVerbs[vIndex];
   *         }
   *         return {};
   *     }
   * ```
   */
  public fun peekNextVerb(): SkPathIter? {
    TODO("Implement peekNextVerb")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathIter::Rec> SkPathIter::next() {
   *     if (vIndex >= fVerbs.size()) {
   *         return {};
   *     }
   *
   *     size_t n = 0;
   *
   *     float w = -1;
   *     SkPathVerb v;
   *     switch (v = fVerbs[vIndex++]) {
   *         case SkPathVerb::kMove:
   *             fClosePointStorage[1] = fPoints[pIndex++]; // remember for close
   *             return Rec{{&fClosePointStorage[1], 1}, w, v};
   *         case SkPathVerb::kLine:  n = 1; break;
   *         case SkPathVerb::kQuad:  n = 2; break;
   *         case SkPathVerb::kConic: n = 2; w = fConics[cIndex++]; break;
   *         case SkPathVerb::kCubic: n = 3; break;
   *         case SkPathVerb::kClose:
   *             SkASSERT(pIndex > 0);
   *             fClosePointStorage[0] = fPoints[pIndex-1];   // the last point we saw
   *             return Rec{fClosePointStorage, w, v};
   *     }
   *
   *     SkASSERT(pIndex > 0);
   *     auto start = pIndex - 1;
   *     SkASSERT(n >= 1 && n <= 3);
   *     pIndex += n;
   *     return Rec{{&fPoints[start], n+1}, w, v};
   *
   * }
   * ```
   */
  public fun next(): Int {
    TODO("Implement next")
  }

  public open class Rec public constructor(
    public var fPoints: Int,
    public var fConicWeight: Float,
    public var fVerb: SkPathIter,
  ) {
    public fun conicWeight(): Float {
      TODO("Implement conicWeight")
    }
  }
}
