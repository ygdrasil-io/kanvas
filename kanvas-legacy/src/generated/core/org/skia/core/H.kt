package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct H { uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
 *     return x ^ y ^ z;
 * }}
 * ```
 */
public open class H {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
   *     return x ^ y ^ z;
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
