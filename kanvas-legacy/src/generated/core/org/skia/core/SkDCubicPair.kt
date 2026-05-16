package org.skia.core

import kotlin.Array

/**
 * C++ original:
 * ```cpp
 * struct SkDCubicPair {
 *     SkDCubic first() const {
 * #ifdef SK_DEBUG
 *         SkDCubic result;
 *         result.debugSet(&pts[0]);
 *         return result;
 * #else
 *         return (const SkDCubic&) pts[0];
 * #endif
 *     }
 *     SkDCubic second() const {
 * #ifdef SK_DEBUG
 *         SkDCubic result;
 *         result.debugSet(&pts[3]);
 *         return result;
 * #else
 *         return (const SkDCubic&) pts[3];
 * #endif
 *     }
 *     SkDPoint pts[7];
 * }
 * ```
 */
public data class SkDCubicPair public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDPoint pts[7]
   * ```
   */
  public var pts: Array<SkDPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkDCubic first() const {
   * #ifdef SK_DEBUG
   *         SkDCubic result;
   *         result.debugSet(&pts[0]);
   *         return result;
   * #else
   *         return (const SkDCubic&) pts[0];
   * #endif
   *     }
   * ```
   */
  public fun first(): SkDCubic {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDCubic second() const {
   * #ifdef SK_DEBUG
   *         SkDCubic result;
   *         result.debugSet(&pts[3]);
   *         return result;
   * #else
   *         return (const SkDCubic&) pts[3];
   * #endif
   *     }
   * ```
   */
  public fun second(): SkDCubic {
    TODO("Implement second")
  }
}
