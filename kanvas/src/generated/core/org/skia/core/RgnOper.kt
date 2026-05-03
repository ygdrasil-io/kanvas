package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class RgnOper {
 * public:
 *     RgnOper(int top, RunArray* array, SkRegion::Op op)
 *         : fMin(gOpMinMax[op].fMin)
 *         , fMax(gOpMinMax[op].fMax)
 *         , fArray(array)
 *         , fTop((SkRegionPriv::RunType)top)  // just a first guess, we might update this
 *         { SkASSERT((unsigned)op <= 3); }
 *
 *     void addSpan(int bottom, const SkRegionPriv::RunType a_runs[],
 *                  const SkRegionPriv::RunType b_runs[]) {
 *         // skip X values and slots for the next Y+intervalCount
 *         int start = fPrevDst + fPrevLen + 2;
 *         // start points to beginning of dst interval
 *         int stop = operate_on_span(a_runs, b_runs, fArray, start, fMin, fMax);
 *         size_t len = SkToSizeT(stop - start);
 *         SkASSERT(len >= 1 && (len & 1) == 1);
 *         SkASSERT(SkRegion_kRunTypeSentinel == (*fArray)[stop - 1]);
 *
 *         // Assert memcmp won't exceed fArray->count().
 *         SkASSERT(fArray->count() >= SkToInt(start + len - 1));
 *         if (fPrevLen == len &&
 *             (1 == len || !memcmp(&(*fArray)[fPrevDst],
 *                                  &(*fArray)[start],
 *                                  (len - 1) * sizeof(SkRegionPriv::RunType)))) {
 *             // update Y value
 *             (*fArray)[fPrevDst - 2] = (SkRegionPriv::RunType)bottom;
 *         } else {    // accept the new span
 *             if (len == 1 && fPrevLen == 0) {
 *                 fTop = (SkRegionPriv::RunType)bottom; // just update our bottom
 *             } else {
 *                 (*fArray)[start - 2] = (SkRegionPriv::RunType)bottom;
 *                 (*fArray)[start - 1] = SkToS32(len >> 1);
 *                 fPrevDst = start;
 *                 fPrevLen = len;
 *             }
 *         }
 *     }
 *
 *     int flush() {
 *         (*fArray)[fStartDst] = fTop;
 *         // Previously reserved enough for TWO sentinals.
 *         SkASSERT(fArray->count() > SkToInt(fPrevDst + fPrevLen));
 *         (*fArray)[fPrevDst + fPrevLen] = SkRegion_kRunTypeSentinel;
 *         return (int)(fPrevDst - fStartDst + fPrevLen + 1);
 *     }
 *
 *     bool isEmpty() const { return 0 == fPrevLen; }
 *
 *     uint8_t fMin, fMax;
 *
 * private:
 *     RunArray* fArray;
 *     int fStartDst = 0;
 *     int fPrevDst = 1;
 *     size_t fPrevLen = 0;  // will never match a length from operate_on_span
 *     SkRegionPriv::RunType fTop;
 * }
 * ```
 */
public data class RgnOper public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t fMin
   * ```
   */
  public var fMin: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fMin, fMax
   * ```
   */
  public var fMax: Int,
  /**
   * C++ original:
   * ```cpp
   * RunArray* fArray
   * ```
   */
  private var fArray: RunArray?,
  /**
   * C++ original:
   * ```cpp
   * int fStartDst = 0
   * ```
   */
  private var fStartDst: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPrevDst = 1
   * ```
   */
  private var fPrevDst: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fPrevLen
   * ```
   */
  private var fPrevLen: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType fTop
   * ```
   */
  private var fTop: SkRegionPrivRunType,
) {
  /**
   * C++ original:
   * ```cpp
   * void addSpan(int bottom, const SkRegionPriv::RunType a_runs[],
   *                  const SkRegionPriv::RunType b_runs[]) {
   *         // skip X values and slots for the next Y+intervalCount
   *         int start = fPrevDst + fPrevLen + 2;
   *         // start points to beginning of dst interval
   *         int stop = operate_on_span(a_runs, b_runs, fArray, start, fMin, fMax);
   *         size_t len = SkToSizeT(stop - start);
   *         SkASSERT(len >= 1 && (len & 1) == 1);
   *         SkASSERT(SkRegion_kRunTypeSentinel == (*fArray)[stop - 1]);
   *
   *         // Assert memcmp won't exceed fArray->count().
   *         SkASSERT(fArray->count() >= SkToInt(start + len - 1));
   *         if (fPrevLen == len &&
   *             (1 == len || !memcmp(&(*fArray)[fPrevDst],
   *                                  &(*fArray)[start],
   *                                  (len - 1) * sizeof(SkRegionPriv::RunType)))) {
   *             // update Y value
   *             (*fArray)[fPrevDst - 2] = (SkRegionPriv::RunType)bottom;
   *         } else {    // accept the new span
   *             if (len == 1 && fPrevLen == 0) {
   *                 fTop = (SkRegionPriv::RunType)bottom; // just update our bottom
   *             } else {
   *                 (*fArray)[start - 2] = (SkRegionPriv::RunType)bottom;
   *                 (*fArray)[start - 1] = SkToS32(len >> 1);
   *                 fPrevDst = start;
   *                 fPrevLen = len;
   *             }
   *         }
   *     }
   * ```
   */
  public fun addSpan(
    bottom: Int,
    aRuns: Array<SkRegionPrivRunType>,
    bRuns: Array<SkRegionPrivRunType>,
  ) {
    TODO("Implement addSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * int flush() {
   *         (*fArray)[fStartDst] = fTop;
   *         // Previously reserved enough for TWO sentinals.
   *         SkASSERT(fArray->count() > SkToInt(fPrevDst + fPrevLen));
   *         (*fArray)[fPrevDst + fPrevLen] = SkRegion_kRunTypeSentinel;
   *         return (int)(fPrevDst - fStartDst + fPrevLen + 1);
   *     }
   * ```
   */
  public fun flush(): Int {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return 0 == fPrevLen; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }
}
