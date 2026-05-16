package org.skia.core

import kotlin.UInt
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_16161616 {
 *     typedef uint64_t Type;
 *     static skvx::Vec<4, uint32_t> Expand(uint64_t x) {
 *         return skvx::cast<uint32_t>(skvx::Vec<4, uint16_t>::Load(&x));
 *     }
 *     static uint64_t Compact(const skvx::Vec<4, uint32_t>& x) {
 *         uint64_t r;
 *         skvx::cast<uint16_t>(x).store(&r);
 *         return r;
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter16161616 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skvx::Vec<4, uint32_t> Expand(uint64_t x) {
     *         return skvx::cast<uint32_t>(skvx::Vec<4, uint16_t>::Load(&x));
     *     }
     * ```
     */
    public fun expand(x: ULong): Vec4<UInt> {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint64_t Compact(const skvx::Vec<4, uint32_t>& x) {
     *         uint64_t r;
     *         skvx::cast<uint16_t>(x).store(&r);
     *         return r;
     *     }
     * ```
     */
    public fun compact(x: Vec4<UInt>): ULong {
      TODO("Implement compact")
    }
  }
}
