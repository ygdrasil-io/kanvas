package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkArc {
 *     enum class Type : bool {
 *         kArc,   // An arc along the perimeter of the oval
 *         kWedge  // A closed wedge that includes the oval's center
 *     };
 *
 *     SkArc() = default;
 *     SkArc(const SkArc& arc) = default;
 *     SkArc& operator=(const SkArc& arc) = default;
 *
 *     const SkRect& oval() const { return fOval; }
 *     SkScalar startAngle() const { return fStartAngle; }
 *     SkScalar sweepAngle() const { return fSweepAngle; }
 *     bool isWedge() const { return fType == Type::kWedge; }
 *
 *     friend bool operator==(const SkArc& a, const SkArc& b) {
 *         return a.fOval == b.fOval && a.fStartAngle == b.fStartAngle &&
 *                a.fSweepAngle == b.fSweepAngle && a.fType == b.fType;
 *     }
 *
 *     friend bool operator!=(const SkArc& a, const SkArc& b) { return !(a == b); }
 *
 *     // Preferred factory that explicitly states which type of arc
 *     static SkArc Make(const SkRect& oval,
 *                       SkScalar startAngleDegrees,
 *                       SkScalar sweepAngleDegrees,
 *                       Type type) {
 *         return SkArc(oval, startAngleDegrees, sweepAngleDegrees, type);
 *     }
 *
 *     // Deprecated factory to assist with legacy code based on `useCenter`
 *     static SkArc Make(const SkRect& oval,
 *                       SkScalar startAngleDegrees,
 *                       SkScalar sweepAngleDegrees,
 *                       bool useCenter) {
 *         return SkArc(
 *                 oval, startAngleDegrees, sweepAngleDegrees, useCenter ? Type::kWedge : Type::kArc);
 *     }
 *
 *     // Bounds of oval containing the arc.
 *     SkRect   fOval = SkRect::MakeEmpty();
 *
 *     // Angle in degrees where the arc begins. Zero means horizontally to the right.
 *     SkScalar fStartAngle = 0;
 *     // Sweep angle in degrees; positive is clockwise.
 *     SkScalar fSweepAngle = 0;
 *
 *     Type     fType = Type::kArc;
 *
 * private:
 *     SkArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle, Type type)
 *             : fOval(oval), fStartAngle(startAngle), fSweepAngle(sweepAngle), fType(type) {}
 * }
 * ```
 */
public data class SkArc public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect   fOval
   * ```
   */
  public var fOval: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fStartAngle
   * ```
   */
  public var fStartAngle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSweepAngle
   * ```
   */
  public var fSweepAngle: Int,
  /**
   * C++ original:
   * ```cpp
   * Type     fType = Type::kArc
   * ```
   */
  public var fType: Type,
) {
  /**
   * C++ original:
   * ```cpp
   * SkArc& operator=(const SkArc& arc) = default
   * ```
   */
  public fun assign(arc: SkArc) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& oval() const { return fOval; }
   * ```
   */
  public fun oval(): Int {
    TODO("Implement oval")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar startAngle() const { return fStartAngle; }
   * ```
   */
  public fun startAngle(): Int {
    TODO("Implement startAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar sweepAngle() const { return fSweepAngle; }
   * ```
   */
  public fun sweepAngle(): Int {
    TODO("Implement sweepAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isWedge() const { return fType == Type::kWedge; }
   * ```
   */
  public fun isWedge(): Boolean {
    TODO("Implement isWedge")
  }

  public enum class Type {
    kArc,
    kWedge,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkArc Make(const SkRect& oval,
     *                       SkScalar startAngleDegrees,
     *                       SkScalar sweepAngleDegrees,
     *                       Type type) {
     *         return SkArc(oval, startAngleDegrees, sweepAngleDegrees, type);
     *     }
     * ```
     */
    public fun make(
      oval: SkRect,
      startAngleDegrees: SkScalar,
      sweepAngleDegrees: SkScalar,
      type: Type,
    ): SkArc {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkArc Make(const SkRect& oval,
     *                       SkScalar startAngleDegrees,
     *                       SkScalar sweepAngleDegrees,
     *                       bool useCenter) {
     *         return SkArc(
     *                 oval, startAngleDegrees, sweepAngleDegrees, useCenter ? Type::kWedge : Type::kArc);
     *     }
     * ```
     */
    public fun make(
      oval: SkRect,
      startAngleDegrees: SkScalar,
      sweepAngleDegrees: SkScalar,
      useCenter: Boolean,
    ): SkArc {
      TODO("Implement make")
    }
  }
}
