package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct I { uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
 *     return y ^ (x | (~z));
 * }}
 * ```
 */
public open class I {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
   *     return y ^ (x | (~z));
   * }
   * ```
   */
  public operator fun invoke(
    x: UInt,
    y: UInt,
    z: UInt,
  ): Int {
    TODO("Implement invoke")
  }
}
