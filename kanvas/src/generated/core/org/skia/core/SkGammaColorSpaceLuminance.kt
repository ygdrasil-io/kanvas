package org.skia.core

import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkGammaColorSpaceLuminance : public SkColorSpaceLuminance {
 *     SkScalar toLuma(SkScalar gamma, SkScalar luminance) const override {
 *         return SkScalarPow(luminance, gamma);
 *     }
 *     SkScalar fromLuma(SkScalar gamma, SkScalar luma) const override {
 *         return SkScalarPow(luma, SkScalarInvert(gamma));
 *     }
 * }
 * ```
 */
public open class SkGammaColorSpaceLuminance : SkColorSpaceLuminance() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar toLuma(SkScalar gamma, SkScalar luminance) const override {
   *         return SkScalarPow(luminance, gamma);
   *     }
   * ```
   */
  public override fun toLuma(gamma: SkScalar, luminance: SkScalar): SkScalar {
    TODO("Implement toLuma")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar fromLuma(SkScalar gamma, SkScalar luma) const override {
   *         return SkScalarPow(luma, SkScalarInvert(gamma));
   *     }
   * ```
   */
  public override fun fromLuma(gamma: SkScalar, luma: SkScalar): SkScalar {
    TODO("Implement fromLuma")
  }
}
