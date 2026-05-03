package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AnalyticClip {
 *     // Indicate which edges are adjacent to circular corners.
 *     enum EdgeFlags {
 *         kLeft_EdgeFlag   = 0b0001,
 *         kTop_EdgeFlag    = 0b0010,
 *         kRight_EdgeFlag  = 0b0100,
 *         kBottom_EdgeFlag = 0b1000,
 *
 *         kNone_EdgeFlag   = 0b0000,
 *         kAll_EdgeFlag    = 0b1111,
 *     };
 *     // These defaults will produce no clip
 *     Rect     fBounds = { 0, 0, 0, 0 }; // Bounds of clip
 *     float    fRadius = 0;              // Circular radius, if any
 *     uint32_t fEdgeFlags = kNone_EdgeFlag;
 *     bool     fInverted = true;
 *
 *     bool isEmpty() const { return fBounds.isEmptyNegativeOrNaN(); }
 *     SkRect edgeSelectRect() const {
 *         return { fEdgeFlags & kLeft_EdgeFlag   ? 1.f : 0.f,
 *                  fEdgeFlags & kTop_EdgeFlag    ? 1.f : 0.f,
 *                  fEdgeFlags & kRight_EdgeFlag  ? 1.f : 0.f,
 *                  fEdgeFlags & kBottom_EdgeFlag ? 1.f : 0.f };
 *     }
 * }
 * ```
 */
public data class AnalyticClip public constructor(
  /**
   * C++ original:
   * ```cpp
   * Rect     fBounds
   * ```
   */
  public var fBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * float    fRadius = 0
   * ```
   */
  public var fRadius: Float,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fEdgeFlags
   * ```
   */
  public var fEdgeFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * bool     fInverted = true
   * ```
   */
  public var fInverted: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fBounds.isEmptyNegativeOrNaN(); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect edgeSelectRect() const {
   *         return { fEdgeFlags & kLeft_EdgeFlag   ? 1.f : 0.f,
   *                  fEdgeFlags & kTop_EdgeFlag    ? 1.f : 0.f,
   *                  fEdgeFlags & kRight_EdgeFlag  ? 1.f : 0.f,
   *                  fEdgeFlags & kBottom_EdgeFlag ? 1.f : 0.f };
   *     }
   * ```
   */
  public fun edgeSelectRect(): Int {
    TODO("Implement edgeSelectRect")
  }

  public enum class EdgeFlags {
    kLeft_EdgeFlag,
    kTop_EdgeFlag,
    kRight_EdgeFlag,
    kBottom_EdgeFlag,
    kNone_EdgeFlag,
    kAll_EdgeFlag,
  }
}
