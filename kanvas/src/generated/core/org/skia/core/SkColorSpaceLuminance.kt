package org.skia.core

import org.skia.foundation.SkColor
import org.skia.foundation.U8CPU
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkColorSpaceLuminance : SkNoncopyable {
 * public:
 *     virtual ~SkColorSpaceLuminance() { }
 *
 *     /** Converts a color component luminance in the color space to a linear luma. */
 *     virtual SkScalar toLuma(SkScalar gamma, SkScalar luminance) const = 0;
 *     /** Converts a linear luma to a color component luminance in the color space. */
 *     virtual SkScalar fromLuma(SkScalar gamma, SkScalar luma) const = 0;
 *
 *     /** Converts a color to a luminance value. */
 *     static U8CPU computeLuminance(SkScalar gamma, SkColor c) {
 *         const SkColorSpaceLuminance& luminance = Fetch(gamma);
 *         SkScalar r = luminance.toLuma(gamma, SkIntToScalar(SkColorGetR(c)) / 255);
 *         SkScalar g = luminance.toLuma(gamma, SkIntToScalar(SkColorGetG(c)) / 255);
 *         SkScalar b = luminance.toLuma(gamma, SkIntToScalar(SkColorGetB(c)) / 255);
 *         SkScalar luma = r * SK_LUM_COEFF_R +
 *                         g * SK_LUM_COEFF_G +
 *                         b * SK_LUM_COEFF_B;
 *         SkASSERT(luma <= SK_Scalar1);
 *         return SkScalarRoundToInt(luminance.fromLuma(gamma, luma) * 255);
 *     }
 *
 *     /** Retrieves the SkColorSpaceLuminance for the given gamma. */
 *     static const SkColorSpaceLuminance& Fetch(SkScalar gamma);
 * }
 * ```
 */
public abstract class SkColorSpaceLuminance : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * virtual SkScalar toLuma(SkScalar gamma, SkScalar luminance) const = 0
   * ```
   */
  public abstract fun toLuma(gamma: SkScalar, luminance: SkScalar): SkScalar

  /**
   * C++ original:
   * ```cpp
   * virtual SkScalar fromLuma(SkScalar gamma, SkScalar luma) const = 0
   * ```
   */
  public abstract fun fromLuma(gamma: SkScalar, luma: SkScalar): SkScalar

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static U8CPU computeLuminance(SkScalar gamma, SkColor c) {
     *         const SkColorSpaceLuminance& luminance = Fetch(gamma);
     *         SkScalar r = luminance.toLuma(gamma, SkIntToScalar(SkColorGetR(c)) / 255);
     *         SkScalar g = luminance.toLuma(gamma, SkIntToScalar(SkColorGetG(c)) / 255);
     *         SkScalar b = luminance.toLuma(gamma, SkIntToScalar(SkColorGetB(c)) / 255);
     *         SkScalar luma = r * SK_LUM_COEFF_R +
     *                         g * SK_LUM_COEFF_G +
     *                         b * SK_LUM_COEFF_B;
     *         SkASSERT(luma <= SK_Scalar1);
     *         return SkScalarRoundToInt(luminance.fromLuma(gamma, luma) * 255);
     *     }
     * ```
     */
    public fun computeLuminance(gamma: SkScalar, c: SkColor): U8CPU {
      TODO("Implement computeLuminance")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkColorSpaceLuminance& SkColorSpaceLuminance::Fetch(SkScalar gamma) {
     *     static SkLinearColorSpaceLuminance gSkLinearColorSpaceLuminance;
     *     static SkGammaColorSpaceLuminance gSkGammaColorSpaceLuminance;
     *     static SkSRGBColorSpaceLuminance gSkSRGBColorSpaceLuminance;
     *
     *     if (0 == gamma) {
     *         return gSkSRGBColorSpaceLuminance;
     *     } else if (SK_Scalar1 == gamma) {
     *         return gSkLinearColorSpaceLuminance;
     *     } else {
     *         return gSkGammaColorSpaceLuminance;
     *     }
     * }
     * ```
     */
    public fun fetch(gamma: SkScalar): SkColorSpaceLuminance {
      TODO("Implement fetch")
    }
  }
}
