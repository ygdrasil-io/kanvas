package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ContourIter {
 * public:
 *     ContourIter(SkSpan<const SkPoint>, SkSpan<const SkPathVerb>, SkSpan<const float> conicWeights);
 *
 *     bool done() const { return fDone; }
 *     // if !done() then these may be called
 *     int count() const { return fCurrPtCount; }
 *     const SkPoint* pts() const { return fCurrPt; }
 *     void next();
 *
 * private:
 *     int fCurrPtCount;
 *     const SkPoint* fCurrPt;
 *     const SkPathVerb* fCurrVerb;
 *     const SkPathVerb* fStopVerbs;
 *     const SkScalar* fCurrConicWeight;
 *     bool fDone;
 *     SkDEBUGCODE(int fContourCounter;)
 * }
 * ```
 */
public data class ContourIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCurrPtCount
   * ```
   */
  private var fCurrPtCount: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkPoint* fCurrPt
   * ```
   */
  private val fCurrPt: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * const SkPathVerb* fCurrVerb
   * ```
   */
  private val fCurrVerb: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * const SkPathVerb* fStopVerbs
   * ```
   */
  private val fStopVerbs: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar* fCurrConicWeight
   * ```
   */
  private val fCurrConicWeight: SkScalar?,
  /**
   * C++ original:
   * ```cpp
   * bool fDone
   * ```
   */
  private var fDone: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool done() const { return fDone; }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCurrPtCount; }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* pts() const { return fCurrPt; }
   * ```
   */
  public fun pts(): SkPoint {
    TODO("Implement pts")
  }

  /**
   * C++ original:
   * ```cpp
   * void ContourIter::next() {
   *     if (fCurrVerb >= fStopVerbs) {
   *         fDone = true;
   *     }
   *     if (fDone) {
   *         return;
   *     }
   *
   *     // skip pts of prev contour
   *     fCurrPt += fCurrPtCount;
   *
   *     SkASSERT(SkPathVerb::kMove == fCurrVerb[0]);
   *     int ptCount = 1;    // moveTo
   *     const SkPathVerb* verbs = fCurrVerb;
   *
   *     for (verbs++; verbs < fStopVerbs; verbs++) {
   *         switch (*verbs) {
   *             case SkPathVerb::kMove:
   *                 goto CONTOUR_END;
   *             case SkPathVerb::kLine:
   *                 ptCount += 1;
   *                 break;
   *             case SkPathVerb::kConic:
   *                 fCurrConicWeight += 1;
   *                 [[fallthrough]];
   *             case SkPathVerb::kQuad:
   *                 ptCount += 2;
   *                 break;
   *             case SkPathVerb::kCubic:
   *                 ptCount += 3;
   *                 break;
   *             case SkPathVerb::kClose:
   *                 break;
   *         }
   *     }
   * CONTOUR_END:
   *     fCurrPtCount = ptCount;
   *     fCurrVerb = verbs;
   *     SkDEBUGCODE(++fContourCounter;)
   * }
   * ```
   */
  public fun next() {
    TODO("Implement next")
  }
}
