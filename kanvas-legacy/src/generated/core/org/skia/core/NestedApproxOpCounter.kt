package org.skia.core

import SkRecords.DrawPicture
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct NestedApproxOpCounter {
 *     int fCount = 0;
 *
 *     template <typename T> void operator()(const T& op) {
 *         fCount += 1;
 *     }
 *     void operator()(const SkRecords::DrawPicture& op) {
 *         fCount += op.picture->approximateOpCount(true);
 *     }
 * }
 * ```
 */
public data class NestedApproxOpCounter public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCount = 0
   * ```
   */
  public var fCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void operator()(const T& op) {
   *         fCount += 1;
   *     }
   * ```
   */
  public operator fun invoke(op: T) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator()(const SkRecords::DrawPicture& op) {
   *         fCount += op.picture->approximateOpCount(true);
   *     }
   * ```
   */
  public operator fun invoke(op: DrawPicture) {
    TODO("Implement invoke")
  }
}
