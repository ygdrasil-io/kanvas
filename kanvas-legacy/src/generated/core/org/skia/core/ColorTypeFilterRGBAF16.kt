package org.skia.core

import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_RGBA_F16 {
 *     typedef uint64_t Type; // SkHalf x4
 *     static skvx::float4 Expand(uint64_t x) {
 *         return from_half(skvx::half4::Load(&x));
 *     }
 *     static uint64_t Compact(const skvx::float4& x) {
 *         uint64_t r;
 *         to_half(x).store(&r);
 *         return r;
 *     }
 * }
 * ```
 */
public open class ColorTypeFilterRGBAF16 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skvx::float4 Expand(uint64_t x) {
     *         return from_half(skvx::half4::Load(&x));
     *     }
     * ```
     */
    public fun expand(x: ULong): Float4 {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint64_t Compact(const skvx::float4& x) {
     *         uint64_t r;
     *         to_half(x).store(&r);
     *         return r;
     *     }
     * ```
     */
    public fun compact(x: Float4): ULong {
      TODO("Implement compact")
    }
  }
}
