package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TextShadow {
 * public:
 *     SkColor fColor = SK_ColorBLACK;
 *     SkPoint fOffset;
 *     double fBlurSigma = 0.0;
 *
 *     TextShadow();
 *
 *     TextShadow(SkColor color, SkPoint offset, double blurSigma);
 *
 *     bool operator==(const TextShadow& other) const;
 *
 *     bool operator!=(const TextShadow& other) const;
 *
 *     bool hasShadow() const;
 * }
 * ```
 */
public data class TextShadow public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColor fColor
   * ```
   */
  public var fColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fOffset
   * ```
   */
  public var fOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * double fBlurSigma = 0.0
   * ```
   */
  public var fBlurSigma: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * bool TextShadow::operator==(const TextShadow& other) const {
   *     if (fColor != other.fColor) return false;
   *     if (fOffset != other.fOffset) return false;
   *     if (fBlurSigma != other.fBlurSigma) return false;
   *
   *     return true;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextShadow::operator!=(const TextShadow& other) const { return !(*this == other); }
   * ```
   */
  public fun hasShadow(): Boolean {
    TODO("Implement hasShadow")
  }
}
