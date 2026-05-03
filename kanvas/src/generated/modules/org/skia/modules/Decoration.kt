package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Decoration {
 *     TextDecoration fType;
 *     TextDecorationMode fMode;
 *     SkColor fColor;
 *     TextDecorationStyle fStyle;
 *     SkScalar fThicknessMultiplier;
 *
 *     bool operator==(const Decoration& other) const {
 *         return this->fType == other.fType &&
 *                this->fMode == other.fMode &&
 *                this->fColor == other.fColor &&
 *                this->fStyle == other.fStyle &&
 *                this->fThicknessMultiplier == other.fThicknessMultiplier;
 *     }
 * }
 * ```
 */
public data class Decoration public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextDecoration fType
   * ```
   */
  public var fType: TextDecoration,
  /**
   * C++ original:
   * ```cpp
   * TextDecorationMode fMode
   * ```
   */
  public var fMode: TextDecorationMode,
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
   * TextDecorationStyle fStyle
   * ```
   */
  public var fStyle: TextDecorationStyle,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fThicknessMultiplier
   * ```
   */
  public var fThicknessMultiplier: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const Decoration& other) const {
   *         return this->fType == other.fType &&
   *                this->fMode == other.fMode &&
   *                this->fColor == other.fColor &&
   *                this->fStyle == other.fStyle &&
   *                this->fThicknessMultiplier == other.fThicknessMultiplier;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
