package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct G { uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
 *     return (x & z) | (y & (~z));
 *     //return ((x ^ y) & z) ^ y; //equivelent but slower
 * }}
 * ```
 */
public open class G {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(uint32_t x, uint32_t y, uint32_t z) {
   *     return (x & z) | (y & (~z));
   *     //return ((x ^ y) & z) ^ y; //equivelent but slower
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
