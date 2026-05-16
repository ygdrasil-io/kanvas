package org.skia.core

import kotlin.UInt
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_565 {
 *     typedef uint16_t Type;
 *     static uint32_t Expand(uint16_t x) {
 *         return (x & ~SK_G16_MASK_IN_PLACE) | ((x & SK_G16_MASK_IN_PLACE) << 16);
 *     }
 *     static uint16_t Compact(uint32_t x) {
 *         return ((x & ~SK_G16_MASK_IN_PLACE) & 0xFFFF) | ((x >> 16) & SK_G16_MASK_IN_PLACE);
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter565 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint32_t Expand(uint16_t x) {
     *         return (x & ~SK_G16_MASK_IN_PLACE) | ((x & SK_G16_MASK_IN_PLACE) << 16);
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
     *         return ((x & ~SK_G16_MASK_IN_PLACE) & 0xFFFF) | ((x >> 16) & SK_G16_MASK_IN_PLACE);
     *     }
     * ```
     */
    public fun compact(x: UInt): UShort {
      TODO("Implement compact")
    }
  }
}
