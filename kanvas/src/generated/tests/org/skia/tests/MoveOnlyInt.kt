package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct MoveOnlyInt {
 *     MoveOnlyInt(int i) : fInt(i) {}
 *     MoveOnlyInt(MoveOnlyInt&& that) : fInt(that.fInt) {}
 *     bool operator==(int i) const { return fInt == i; }
 *     int fInt;
 * }
 * ```
 */
public data class MoveOnlyInt public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fInt
   * ```
   */
  public var fInt: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(int i) const { return fInt == i; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
