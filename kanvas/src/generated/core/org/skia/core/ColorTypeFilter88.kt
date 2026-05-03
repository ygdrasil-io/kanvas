package org.skia.core

import kotlin.UInt
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_88 {
 *     typedef uint16_t Type;
 *     static uint32_t Expand(uint16_t x) {
 *         return (x & 0xFF) | ((x & ~0xFF) << 8);
 *     }
 *     static uint16_t Compact(uint32_t x) {
 *         return (x & 0xFF) | ((x >> 8) & ~0xFF);
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter88 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint32_t Expand(uint16_t x) {
     *         return (x & 0xFF) | ((x & ~0xFF) << 8);
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
     *         return (x & 0xFF) | ((x >> 8) & ~0xFF);
     *     }
     * ```
     */
    public fun compact(x: UInt): UShort {
      TODO("Implement compact")
    }
  }
}
