package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TransformPropertyValue {
 *     SkPoint  fAnchorPoint,
 *              fPosition;
 *     SkVector fScale;
 *     SkScalar fRotation,
 *              fSkew,
 *              fSkewAxis;
 *
 *     bool operator==(const TransformPropertyValue& other) const;
 *     bool operator!=(const TransformPropertyValue& other) const;
 * }
 * ```
 */
public data class TransformPropertyValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fAnchorPoint
   * ```
   */
  public var fAnchorPoint: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fAnchorPoint,
   *              fPosition
   * ```
   */
  public var fPosition: Int,
  /**
   * C++ original:
   * ```cpp
   * SkVector fScale
   * ```
   */
  public var fScale: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotation
   * ```
   */
  public var fRotation: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotation,
   *              fSkew
   * ```
   */
  public var fSkew: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotation,
   *              fSkew,
   *              fSkewAxis
   * ```
   */
  public var fSkewAxis: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool TransformPropertyValue::operator==(const TransformPropertyValue& other) const {
   *     return this->fAnchorPoint == other.fAnchorPoint
   *         && this->fPosition    == other.fPosition
   *         && this->fScale       == other.fScale
   *         && this->fSkew        == other.fSkew
   *         && this->fSkewAxis    == other.fSkewAxis;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
