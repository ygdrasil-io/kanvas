package org.skia.core

import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkLinearColorSpaceLuminance : public SkColorSpaceLuminance {
 *     SkScalar toLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luminance) const override {
 *         SkASSERT(SK_Scalar1 == gamma);
 *         return luminance;
 *     }
 *     SkScalar fromLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luma) const override {
 *         SkASSERT(SK_Scalar1 == gamma);
 *         return luma;
 *     }
 * }
 * ```
 */
public open class SkLinearColorSpaceLuminance : SkColorSpaceLuminance() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar toLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luminance) const override {
   *         SkASSERT(SK_Scalar1 == gamma);
   *         return luminance;
   *     }
   * ```
   */
  public override fun toLuma(luminance: SkScalar): SkScalar {
    TODO("Implement toLuma")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar fromLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luma) const override {
   *         SkASSERT(SK_Scalar1 == gamma);
   *         return luma;
   *     }
   * ```
   */
  public override fun fromLuma(luma: SkScalar): SkScalar {
    TODO("Implement fromLuma")
  }
}
