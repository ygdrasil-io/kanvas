package org.skia.core

import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class ScaledDividerU32 {
 * public:
 *     explicit ScaledDividerU32(uint32_t divisor)
 *             : fDivisorFactor{(uint32_t)(std::round((1.0 / divisor) * (1ull << 32)))}
 *             , fHalf{(divisor + 1) >> 1} {
 *         assert(divisor > 1);
 *     }
 *
 *     Vec<4, uint32_t> divide(const Vec<4, uint32_t>& numerator) const {
 * #if SKVX_USE_SIMD && defined(SK_ARM_HAS_NEON)
 *         uint64x2_t hi = vmull_n_u32(vget_high_u32(to_vext(numerator)), fDivisorFactor);
 *         uint64x2_t lo = vmull_n_u32(vget_low_u32(to_vext(numerator)),  fDivisorFactor);
 *
 *         return to_vec<4, uint32_t>(vcombine_u32(vshrn_n_u64(lo,32), vshrn_n_u64(hi,32)));
 * #else
 *         return cast<uint32_t>((cast<uint64_t>(numerator) * fDivisorFactor) >> 32);
 * #endif
 *     }
 *
 *     uint32_t half() const { return fHalf; }
 *     uint32_t divisorFactor() const { return fDivisorFactor; }
 *
 * private:
 *     const uint32_t fDivisorFactor;
 *     const uint32_t fHalf;
 * }
 * ```
 */
public data class ScaledDividerU32 public constructor(
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fDivisorFactor
   * ```
   */
  private val fDivisorFactor: UInt,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fHalf
   * ```
   */
  private val fHalf: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * Vec<4, uint32_t> divide(const Vec<4, uint32_t>& numerator) const {
   * #if SKVX_USE_SIMD && defined(SK_ARM_HAS_NEON)
   *         uint64x2_t hi = vmull_n_u32(vget_high_u32(to_vext(numerator)), fDivisorFactor);
   *         uint64x2_t lo = vmull_n_u32(vget_low_u32(to_vext(numerator)),  fDivisorFactor);
   *
   *         return to_vec<4, uint32_t>(vcombine_u32(vshrn_n_u64(lo,32), vshrn_n_u64(hi,32)));
   * #else
   *         return cast<uint32_t>((cast<uint64_t>(numerator) * fDivisorFactor) >> 32);
   * #endif
   *     }
   * ```
   */
  public fun divide(numerator: Vec4<UInt>): Vec4<UInt> {
    TODO("Implement divide")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t half() const { return fHalf; }
   * ```
   */
  public fun half(): UInt {
    TODO("Implement half")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t divisorFactor() const { return fDivisorFactor; }
   * ```
   */
  public fun divisorFactor(): UInt {
    TODO("Implement divisorFactor")
  }
}
