package org.skia.core

import kotlin.UInt
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_16 {
 *     typedef uint16_t Type;
 *     static uint32_t Expand(uint16_t x) {
 *         return x;
 *     }
 *     static uint16_t Compact(uint32_t x) {
 *         return (uint16_t) x;
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter16 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint32_t Expand(uint16_t x) {
     *         return x;
     *     }
     * ```
     */
    public fun expand(x: UShort): UInt {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint16_t Compact(uint32_t x) {
     *         return (uint16_t) x;
     *     }
     * ```
     */
    public fun compact(x: UInt): UShort {
      TODO("Implement compact")
    }
  }
}
