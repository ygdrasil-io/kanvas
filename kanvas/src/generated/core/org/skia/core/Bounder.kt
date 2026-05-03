package org.skia.core

import kotlin.Boolean
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct Bounder {
 *     SkRect  fBounds;
 *     bool    fHasBounds;
 *
 *     Bounder(const SkRect& r, const SkPaint& paint) {
 *         if ((fHasBounds = paint.canComputeFastBounds())) {
 *             fBounds = paint.computeFastBounds(r, &fBounds);
 *         }
 *     }
 *
 *     bool hasBounds() const { return fHasBounds; }
 *     const SkRect* bounds() const { return fHasBounds ? &fBounds : nullptr; }
 *     operator const SkRect* () const { return this->bounds(); }
 * }
 * ```
 */
public data class Bounder public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect  fBounds
   * ```
   */
  public var fBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * bool    fHasBounds
   * ```
   */
  public var fHasBounds: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool hasBounds() const { return fHasBounds; }
   * ```
   */
  public fun hasBounds(): Boolean {
    TODO("Implement hasBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect* bounds() const { return fHasBounds ? &fBounds : nullptr; }
   * ```
   */
  public fun bounds(): SkRect {
    TODO("Implement bounds")
  }
}
