package org.skia.modules

import kotlin.Int
import kotlin.Long
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct Int96 {
 *     int64_t hi;
 *     uint32_t lo;
 *
 *     static Int96 Make(int32_t a);
 *     static Int96 Make(int64_t a);
 * }
 * ```
 */
public data class Int96 public constructor(
  /**
   * C++ original:
   * ```cpp
   * int64_t hi
   * ```
   */
  public var hi: Long,
  /**
   * C++ original:
   * ```cpp
   * uint32_t lo
   * ```
   */
  public var lo: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * Int96 Int96::Make(int64_t a) {
   *     return {a >> 32, (uint32_t)(a & 0xFFFFFFFF)};
   * }
   * ```
   */
  public fun make(a: Int): Int96 {
    TODO("Implement make")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Int96 Make(int32_t a)
     * ```
     */
    public fun make(a: Int): Int96 {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static Int96 Make(int64_t a)
     * ```
     */
    public fun make(a: Long): Int96 {
      TODO("Implement make")
    }
  }
}
