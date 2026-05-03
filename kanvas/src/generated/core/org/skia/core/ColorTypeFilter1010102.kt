package org.skia.core

import kotlin.UInt
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_1010102 {
 *     typedef uint32_t Type;
 *     static uint64_t Expand(uint64_t x) {
 *         return (((x      ) & 0x3ff)      ) |
 *         (((x >> 10) & 0x3ff) << 20) |
 *         (((x >> 20) & 0x3ff) << 40) |
 *         (((x >> 30) & 0x3  ) << 60);
 *     }
 *     static uint32_t Compact(uint64_t x) {
 *         return (((x      ) & 0x3ff)      ) |
 *         (((x >> 20) & 0x3ff) << 10) |
 *         (((x >> 40) & 0x3ff) << 20) |
 *         (((x >> 60) & 0x3  ) << 30);
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter1010102 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint64_t Expand(uint64_t x) {
     *         return (((x      ) & 0x3ff)      ) |
     *         (((x >> 10) & 0x3ff) << 20) |
     *         (((x >> 20) & 0x3ff) << 40) |
     *         (((x >> 30) & 0x3  ) << 60);
     *     }
     * ```
     */
    public fun expand(x: ULong): ULong {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t Compact(uint64_t x) {
     *         return (((x      ) & 0x3ff)      ) |
     *         (((x >> 20) & 0x3ff) << 10) |
     *         (((x >> 40) & 0x3ff) << 20) |
     *         (((x >> 60) & 0x3  ) << 30);
     *     }
     * ```
     */
    public fun compact(x: ULong): UInt {
      TODO("Implement compact")
    }
  }
}
