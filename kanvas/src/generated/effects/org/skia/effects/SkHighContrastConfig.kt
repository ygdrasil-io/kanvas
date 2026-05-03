package org.skia.effects

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkHighContrastConfig {
 *     enum class InvertStyle {
 *         kNoInvert,
 *         kInvertBrightness,
 *         kInvertLightness,
 *
 *         kLast = kInvertLightness
 *     };
 *
 *     SkHighContrastConfig() {
 *         fGrayscale = false;
 *         fInvertStyle = InvertStyle::kNoInvert;
 *         fContrast = 0.0f;
 *     }
 *
 *     SkHighContrastConfig(bool grayscale,
 *                          InvertStyle invertStyle,
 *                          SkScalar contrast)
 *         : fGrayscale(grayscale)
 *         , fInvertStyle(invertStyle)
 *         , fContrast(contrast) {}
 *
 *     // Returns true if all of the fields are set within the valid range.
 *     bool isValid() const {
 *         return fInvertStyle >= InvertStyle::kNoInvert &&
 *                fInvertStyle <= InvertStyle::kInvertLightness &&
 *                fContrast >= -1.0 &&
 *                fContrast <= 1.0;
 *     }
 *
 *     // If true, the color will be converted to grayscale.
 *     bool fGrayscale;
 *
 *     // Whether to invert brightness, lightness, or neither.
 *     InvertStyle fInvertStyle;
 *
 *     // After grayscale and inverting, the contrast can be adjusted linearly.
 *     // The valid range is -1.0 through 1.0, where 0.0 is no adjustment.
 *     SkScalar  fContrast;
 * }
 * ```
 */
public data class SkHighContrastConfig public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fGrayscale
   * ```
   */
  public var fGrayscale: Boolean,
  /**
   * C++ original:
   * ```cpp
   * InvertStyle fInvertStyle
   * ```
   */
  public var fInvertStyle: InvertStyle,
  /**
   * C++ original:
   * ```cpp
   * SkScalar  fContrast
   * ```
   */
  public var fContrast: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isValid() const {
   *         return fInvertStyle >= InvertStyle::kNoInvert &&
   *                fInvertStyle <= InvertStyle::kInvertLightness &&
   *                fContrast >= -1.0 &&
   *                fContrast <= 1.0;
   *     }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  public enum class InvertStyle {
    kNoInvert,
    kInvertBrightness,
    kInvertLightness,
    kLast,
  }
}
