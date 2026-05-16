package org.skia.core

import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_8 {
 *     typedef uint8_t Type;
 *     static unsigned Expand(unsigned x) {
 *         return x;
 *     }
 *     static uint8_t Compact(unsigned x) {
 *         return (uint8_t)x;
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter8 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static unsigned Expand(unsigned x) {
     *         return x;
     *     }
     * ```
     */
    public fun expand(x: UInt): UInt {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint8_t Compact(unsigned x) {
     *         return (uint8_t)x;
     *     }
     * ```
     */
    public fun compact(x: UInt): UByte {
      TODO("Implement compact")
    }
  }
}
