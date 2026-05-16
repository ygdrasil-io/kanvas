package org.skia.core

import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkSRGBColorSpaceLuminance : public SkColorSpaceLuminance {
 *     SkScalar toLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luminance) const override {
 *         SkASSERT(0 == gamma);
 *         //The magic numbers are derived from the sRGB specification.
 *         //See http://www.color.org/chardata/rgb/srgb.xalter .
 *         if (luminance <= 0.04045f) {
 *             return luminance / 12.92f;
 *         }
 *         return SkScalarPow((luminance + 0.055f) / 1.055f,
 *                         2.4f);
 *     }
 *     SkScalar fromLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luma) const override {
 *         SkASSERT(0 == gamma);
 *         //The magic numbers are derived from the sRGB specification.
 *         //See http://www.color.org/chardata/rgb/srgb.xalter .
 *         if (luma <= 0.0031308f) {
 *             return luma * 12.92f;
 *         }
 *         return 1.055f * SkScalarPow(luma, SkScalarInvert(2.4f))
 *                - 0.055f;
 *     }
 * }
 * ```
 */
public open class SkSRGBColorSpaceLuminance : SkColorSpaceLuminance() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar toLuma(SkScalar SkDEBUGCODE(gamma), SkScalar luminance) const override {
   *         SkASSERT(0 == gamma);
   *         //The magic numbers are derived from the sRGB specification.
   *         //See http://www.color.org/chardata/rgb/srgb.xalter .
   *         if (luminance <= 0.04045f) {
   *             return luminance / 12.92f;
   *         }
   *         return SkScalarPow((luminance + 0.055f) / 1.055f,
   *                         2.4f);
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
   *         SkASSERT(0 == gamma);
   *         //The magic numbers are derived from the sRGB specification.
   *         //See http://www.color.org/chardata/rgb/srgb.xalter .
   *         if (luma <= 0.0031308f) {
   *             return luma * 12.92f;
   *         }
   *         return 1.055f * SkScalarPow(luma, SkScalarInvert(2.4f))
   *                - 0.055f;
   *     }
   * ```
   */
  public override fun fromLuma(luma: SkScalar): SkScalar {
    TODO("Implement fromLuma")
  }
}
