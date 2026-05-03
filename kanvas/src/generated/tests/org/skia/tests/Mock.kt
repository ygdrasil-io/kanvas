package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Mock {
 *     int fValue;
 *     int fPriority;
 *     mutable int fIndex;
 *
 *     static bool LessP(Mock* const& a, Mock* const& b) { return a->fPriority < b->fPriority; }
 *     static int* PQIndex(Mock* const& mock) { return &mock->fIndex; }
 *
 *     bool operator== (const Mock& that) const {
 *         return fValue == that.fValue && fPriority == that.fPriority;
 *     }
 *     bool operator!= (const Mock& that) const { return !(*this == that); }
 * }
 * ```
 */
public data class Mock public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fValue
   * ```
   */
  public var fValue: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPriority
   * ```
   */
  public var fPriority: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable int fIndex
   * ```
   */
  public var fIndex: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator== (const Mock& that) const {
   *         return fValue == that.fValue && fPriority == that.fPriority;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool LessP(Mock* const& a, Mock* const& b) { return a->fPriority < b->fPriority; }
     * ```
     */
    public fun lessP(a: Int, b: Int): Boolean {
      TODO("Implement lessP")
    }

    /**
     * C++ original:
     * ```cpp
     * static int* PQIndex(Mock* const& mock) { return &mock->fIndex; }
     * ```
     */
    public fun pQIndex(mock: Int): Int {
      TODO("Implement pQIndex")
    }
  }
}
