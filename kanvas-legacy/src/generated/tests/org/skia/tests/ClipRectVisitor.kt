package org.skia.tests

import SkRecords.ClipRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct ClipRectVisitor {
 *     skiatest::Reporter* r;
 *
 *     template <typename T>
 *     SkRect operator()(const T&) {
 *         REPORTER_ASSERT(r, false, "unexpected record");
 *         return {1,1,0,0};
 *     }
 *
 *     SkRect operator()(const SkRecords::ClipRect& op) {
 *         return op.rect;
 *     }
 * }
 * ```
 */
public data class ClipRectVisitor public constructor(
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* r
   * ```
   */
  public var r: Reporter?,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     SkRect operator()(const T&) {
   *         REPORTER_ASSERT(r, false, "unexpected record");
   *         return {1,1,0,0};
   *     }
   * ```
   */
  public operator fun <T> invoke(param0: T): SkRect {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect operator()(const SkRecords::ClipRect& op) {
   *         return op.rect;
   *     }
   * ```
   */
  public operator fun invoke(op: ClipRect): SkRect {
    TODO("Implement invoke")
  }
}
