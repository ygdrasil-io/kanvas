package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct F { uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
 *     //return (x & y) | ((~x) & z);
 *     return ((y ^ z) & x) ^ z; //equivelent but faster
 * }}
 * ```
 */
public open class F {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
   *     //return (x & y) | ((~x) & z);
   *     return ((y ^ z) & x) ^ z; //equivelent but faster
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
