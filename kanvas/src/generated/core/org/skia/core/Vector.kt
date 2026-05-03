package org.skia.core

import kotlin.Boolean
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct Vector {
 *     SkScalar fX;
 *     SkScalar fY;
 *
 *     Vector() = default;
 *     Vector(SkScalar x, SkScalar y) : fX(x), fY(y) {}
 *     explicit Vector(const SkVector& v) : fX(v.fX), fY(v.fY) {}
 *
 *     bool isFinite() const { return SkIsFinite(fX, fY); }
 * }
 * ```
 */
public data class Vector public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX
   * ```
   */
  public var fX: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fY
   * ```
   */
  public var fY: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const { return SkIsFinite(fX, fY); }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }
}
