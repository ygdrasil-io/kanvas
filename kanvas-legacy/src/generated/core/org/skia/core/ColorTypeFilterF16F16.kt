package org.skia.core

import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct ColorTypeFilter_F16F16 {
 *     typedef uint32_t Type;
 *     static skvx::float4 Expand(uint32_t x) {
 *         uint64_t x4 = (uint64_t)x; // // add 0s out to four lanes (0,0,x,x)
 *         return from_half(skvx::half4::Load(&x4));
 *     }
 *     static uint32_t Compact(const skvx::float4& x) {
 *         uint64_t r;
 *         to_half(x).store(&r);
 *         return (uint32_t) (r & 0xFFFFFFFF);  // but ignore the extra 2 here
 *     }
 * }
 * ```
 */
public open class ColorTypeFilterF16F16 {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skvx::float4 Expand(uint32_t x) {
     *         uint64_t x4 = (uint64_t)x; // // add 0s out to four lanes (0,0,x,x)
     *         return from_half(skvx::half4::Load(&x4));
     *     }
     * ```
     */
    public fun expand(x: UInt): Float4 {
      TODO("Implement expand")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t Compact(const skvx::float4& x) {
     *         uint64_t r;
     *         to_half(x).store(&r);
     *         return (uint32_t) (r & 0xFFFFFFFF);  // but ignore the extra 2 here
     *     }
     * ```
     */
    public fun compact(x: Float4): UInt {
      TODO("Implement compact")
    }
  }
}
