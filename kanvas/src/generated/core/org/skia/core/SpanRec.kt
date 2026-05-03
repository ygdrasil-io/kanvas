package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct spanRec {
 *     const SkRegionPriv::RunType*    fA_runs;
 *     const SkRegionPriv::RunType*    fB_runs;
 *     int                         fA_left, fA_rite, fB_left, fB_rite;
 *     int                         fLeft, fRite, fInside;
 *
 *     void init(const SkRegionPriv::RunType a_runs[],
 *               const SkRegionPriv::RunType b_runs[]) {
 *         fA_left = *a_runs++;
 *         fA_rite = *a_runs++;
 *         fB_left = *b_runs++;
 *         fB_rite = *b_runs++;
 *
 *         fA_runs = a_runs;
 *         fB_runs = b_runs;
 *     }
 *
 *     bool done() const {
 *         SkASSERT(fA_left <= SkRegion_kRunTypeSentinel);
 *         SkASSERT(fB_left <= SkRegion_kRunTypeSentinel);
 *         return fA_left == SkRegion_kRunTypeSentinel &&
 *                fB_left == SkRegion_kRunTypeSentinel;
 *     }
 *
 *     void next() {
 *         assert_valid_pair(fA_left, fA_rite);
 *         assert_valid_pair(fB_left, fB_rite);
 *
 *         int     inside, left, rite SK_INIT_TO_AVOID_WARNING;
 *         bool    a_flush = false;
 *         bool    b_flush = false;
 *
 *         int a_left = fA_left;
 *         int a_rite = fA_rite;
 *         int b_left = fB_left;
 *         int b_rite = fB_rite;
 *
 *         if (a_left < b_left) {
 *             inside = 1;
 *             left = a_left;
 *             if (a_rite <= b_left) {   // [...] <...>
 *                 rite = a_rite;
 *                 a_flush = true;
 *             } else { // [...<..]...> or [...<...>...]
 *                 rite = a_left = b_left;
 *             }
 *         } else if (b_left < a_left) {
 *             inside = 2;
 *             left = b_left;
 *             if (b_rite <= a_left) {   // [...] <...>
 *                 rite = b_rite;
 *                 b_flush = true;
 *             } else {    // [...<..]...> or [...<...>...]
 *                 rite = b_left = a_left;
 *             }
 *         } else {    // a_left == b_left
 *             inside = 3;
 *             left = a_left;  // or b_left
 *             if (a_rite <= b_rite) {
 *                 rite = b_left = a_rite;
 *                 a_flush = true;
 *             }
 *             if (b_rite <= a_rite) {
 *                 rite = a_left = b_rite;
 *                 b_flush = true;
 *             }
 *         }
 *
 *         if (a_flush) {
 *             a_left = *fA_runs++;
 *             a_rite = *fA_runs++;
 *         }
 *         if (b_flush) {
 *             b_left = *fB_runs++;
 *             b_rite = *fB_runs++;
 *         }
 *
 *         SkASSERT(left <= rite);
 *
 *         // now update our state
 *         fA_left = a_left;
 *         fA_rite = a_rite;
 *         fB_left = b_left;
 *         fB_rite = b_rite;
 *
 *         fLeft = left;
 *         fRite = rite;
 *         fInside = inside;
 *     }
 * }
 * ```
 */
public data class SpanRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkRegionPriv::RunType*    fA_runs
   * ```
   */
  public val fARuns: SkRegionPrivRunType?,
  /**
   * C++ original:
   * ```cpp
   * const SkRegionPriv::RunType*    fB_runs
   * ```
   */
  public val fBRuns: SkRegionPrivRunType?,
  /**
   * C++ original:
   * ```cpp
   * int                         fA_left
   * ```
   */
  public var fALeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fA_left, fA_rite
   * ```
   */
  public var fARite: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fA_left, fA_rite, fB_left
   * ```
   */
  public var fBLeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fA_left, fA_rite, fB_left, fB_rite
   * ```
   */
  public var fBRite: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fLeft
   * ```
   */
  public var fLeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fLeft, fRite
   * ```
   */
  public var fRite: Int,
  /**
   * C++ original:
   * ```cpp
   * int                         fLeft, fRite, fInside
   * ```
   */
  public var fInside: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void init(const SkRegionPriv::RunType a_runs[],
   *               const SkRegionPriv::RunType b_runs[]) {
   *         fA_left = *a_runs++;
   *         fA_rite = *a_runs++;
   *         fB_left = *b_runs++;
   *         fB_rite = *b_runs++;
   *
   *         fA_runs = a_runs;
   *         fB_runs = b_runs;
   *     }
   * ```
   */
  public fun `init`(aRuns: Array<SkRegionPrivRunType>, bRuns: Array<SkRegionPrivRunType>) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         SkASSERT(fA_left <= SkRegion_kRunTypeSentinel);
   *         SkASSERT(fB_left <= SkRegion_kRunTypeSentinel);
   *         return fA_left == SkRegion_kRunTypeSentinel &&
   *                fB_left == SkRegion_kRunTypeSentinel;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * void next() {
   *         assert_valid_pair(fA_left, fA_rite);
   *         assert_valid_pair(fB_left, fB_rite);
   *
   *         int     inside, left, rite SK_INIT_TO_AVOID_WARNING;
   *         bool    a_flush = false;
   *         bool    b_flush = false;
   *
   *         int a_left = fA_left;
   *         int a_rite = fA_rite;
   *         int b_left = fB_left;
   *         int b_rite = fB_rite;
   *
   *         if (a_left < b_left) {
   *             inside = 1;
   *             left = a_left;
   *             if (a_rite <= b_left) {   // [...] <...>
   *                 rite = a_rite;
   *                 a_flush = true;
   *             } else { // [...<..]...> or [...<...>...]
   *                 rite = a_left = b_left;
   *             }
   *         } else if (b_left < a_left) {
   *             inside = 2;
   *             left = b_left;
   *             if (b_rite <= a_left) {   // [...] <...>
   *                 rite = b_rite;
   *                 b_flush = true;
   *             } else {    // [...<..]...> or [...<...>...]
   *                 rite = b_left = a_left;
   *             }
   *         } else {    // a_left == b_left
   *             inside = 3;
   *             left = a_left;  // or b_left
   *             if (a_rite <= b_rite) {
   *                 rite = b_left = a_rite;
   *                 a_flush = true;
   *             }
   *             if (b_rite <= a_rite) {
   *                 rite = a_left = b_rite;
   *                 b_flush = true;
   *             }
   *         }
   *
   *         if (a_flush) {
   *             a_left = *fA_runs++;
   *             a_rite = *fA_runs++;
   *         }
   *         if (b_flush) {
   *             b_left = *fB_runs++;
   *             b_rite = *fB_runs++;
   *         }
   *
   *         SkASSERT(left <= rite);
   *
   *         // now update our state
   *         fA_left = a_left;
   *         fA_rite = a_rite;
   *         fB_left = b_left;
   *         fB_rite = b_rite;
   *
   *         fLeft = left;
   *         fRite = rite;
   *         fInside = inside;
   *     }
   * ```
   */
  public fun next() {
    TODO("Implement next")
  }
}
