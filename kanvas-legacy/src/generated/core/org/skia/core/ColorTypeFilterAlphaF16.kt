package org.skia.core

import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_Alpha_F16 {
 *     typedef uint16_t Type;
 *     static skvx::float4 Expand(uint16_t x) {
 *         uint64_t x4 = (uint64_t)x; // add 0s out to four lanes (0,0,0,x)
 *         return from_half(skvx::half4::Load(&x4));
 *     }
 *     static uint16_t Compact(const skvx::float4& x) {
 *         uint64_t r;
 *         to_half(x).store(&r);
 *         return r & 0xFFFF;  // but ignore the extra 3 here
 *     }
 * }
 * ```
 */
public open class ColorTypeFilterAlphaF16 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skvx::float4 Expand(uint16_t x) {
     *         uint64_t x4 = (uint64_t)x; // add 0s out to four lanes (0,0,0,x)
     *         return from_half(skvx::half4::Load(&x4));
     *     }
     * ```
     */
    public fun expand(x: UShort): Float4 {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint16_t Compact(const skvx::float4& x) {
     *         uint64_t r;
     *         to_half(x).store(&r);
     *         return r & 0xFFFF;  // but ignore the extra 3 here
     *     }
     * ```
     */
    public fun compact(x: Float4): UShort {
      TODO("Implement compact")
    }
  }
}
