package org.skia.core

import kotlin.UInt
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_8888 {
 *     typedef uint32_t Type;
 *     static skvx::Vec<4, uint16_t> Expand(uint32_t x) {
 *         return skvx::cast<uint16_t>(skvx::byte4::Load(&x));
 *     }
 *     static uint32_t Compact(const skvx::Vec<4, uint16_t>& x) {
 *         uint32_t r;
 *         skvx::cast<uint8_t>(x).store(&r);
 *         return r;
 *     }
 * }
 * ```
 */
public open class ColorTypeFilter8888 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skvx::Vec<4, uint16_t> Expand(uint32_t x) {
     *         return skvx::cast<uint16_t>(skvx::byte4::Load(&x));
     *     }
     * ```
     */
    public fun expand(x: UInt): Vec4<UShort> {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t Compact(const skvx::Vec<4, uint16_t>& x) {
     *         uint32_t r;
     *         skvx::cast<uint8_t>(x).store(&r);
     *         return r;
     *     }
     * ```
     */
    public fun compact(x: Vec4<UShort>): UInt {
      TODO("Implement compact")
    }
  }
}
