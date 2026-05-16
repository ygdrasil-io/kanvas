package org.skia.utils

import kotlin.Boolean
import kotlin.UShort
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct OffsetEdge {
 *     OffsetEdge*   fPrev;
 *     OffsetEdge*   fNext;
 *     OffsetSegment fOffset;
 *     SkPoint       fIntersection;
 *     SkScalar      fTValue;
 *     uint16_t      fIndex;
 *     uint16_t      fEnd;
 *
 *     void init(uint16_t start = 0, uint16_t end = 0) {
 *         fIntersection = fOffset.fP0;
 *         fTValue = SK_ScalarMin;
 *         fIndex = start;
 *         fEnd = end;
 *     }
 *
 *     // special intersection check that looks for endpoint intersection
 *     bool checkIntersection(const OffsetEdge* that,
 *                            SkPoint* p, SkScalar* s, SkScalar* t) {
 *         if (this->fEnd == that->fIndex) {
 *             SkPoint p1 = this->fOffset.fP0 + this->fOffset.fV;
 *             if (SkPointPriv::EqualsWithinTolerance(p1, that->fOffset.fP0)) {
 *                 *p = p1;
 *                 *s = SK_Scalar1;
 *                 *t = 0;
 *                 return true;
 *             }
 *         }
 *
 *         return compute_intersection(this->fOffset, that->fOffset, p, s, t);
 *     }
 *
 *     // computes the line intersection and then the "distance" from that to this
 *     // this is really a signed squared distance, where negative means that
 *     // the intersection lies inside this->fOffset
 *     SkScalar computeCrossingDistance(const OffsetEdge* that) {
 *         const OffsetSegment& s0 = this->fOffset;
 *         const OffsetSegment& s1 = that->fOffset;
 *         const SkVector& v0 = s0.fV;
 *         const SkVector& v1 = s1.fV;
 *
 *         SkScalar denom = v0.cross(v1);
 *         if (SkScalarNearlyZero(denom, kCrossTolerance)) {
 *             // segments are parallel
 *             return SK_ScalarMax;
 *         }
 *
 *         SkVector w = s1.fP0 - s0.fP0;
 *         SkScalar localS = w.cross(v1) / denom;
 *         if (localS < 0) {
 *             localS = -localS;
 *         } else {
 *             localS -= SK_Scalar1;
 *         }
 *
 *         localS *= SkScalarAbs(localS);
 *         localS *= v0.dot(v0);
 *
 *         return localS;
 *     }
 *
 * }
 * ```
 */
public abstract class OffsetEdge public constructor(
  /**
   * C++ original:
   * ```cpp
   * OffsetEdge*   fPrev
   * ```
   */
  public var fPrev: OffsetEdge?,
  /**
   * C++ original:
   * ```cpp
   * OffsetEdge*   fNext
   * ```
   */
  public var fNext: OffsetEdge?,
  /**
   * C++ original:
   * ```cpp
   * OffsetSegment fOffset
   * ```
   */
  public var fOffset: OffsetSegment,
  /**
   * C++ original:
   * ```cpp
   * SkPoint       fIntersection
   * ```
   */
  public var fIntersection: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkScalar      fTValue
   * ```
   */
  public var fTValue: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * uint16_t      fIndex
   * ```
   */
  public var fIndex: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t      fEnd
   * ```
   */
  public var fEnd: UShort,
) {
  /**
   * C++ original:
   * ```cpp
   * void init(uint16_t start = 0, uint16_t end = 0) {
   *         fIntersection = fOffset.fP0;
   *         fTValue = SK_ScalarMin;
   *         fIndex = start;
   *         fEnd = end;
   *     }
   * ```
   */
  public abstract fun `init`(start: UShort = TODO(), end: UShort = TODO())

  /**
   * C++ original:
   * ```cpp
   * bool checkIntersection(const OffsetEdge* that,
   *                            SkPoint* p, SkScalar* s, SkScalar* t) {
   *         if (this->fEnd == that->fIndex) {
   *             SkPoint p1 = this->fOffset.fP0 + this->fOffset.fV;
   *             if (SkPointPriv::EqualsWithinTolerance(p1, that->fOffset.fP0)) {
   *                 *p = p1;
   *                 *s = SK_Scalar1;
   *                 *t = 0;
   *                 return true;
   *             }
   *         }
   *
   *         return compute_intersection(this->fOffset, that->fOffset, p, s, t);
   *     }
   * ```
   */
  public fun checkIntersection(
    that: OffsetEdge?,
    p: SkPoint?,
    s: SkScalar?,
    t: SkScalar?,
  ): Boolean {
    TODO("Implement checkIntersection")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar computeCrossingDistance(const OffsetEdge* that) {
   *         const OffsetSegment& s0 = this->fOffset;
   *         const OffsetSegment& s1 = that->fOffset;
   *         const SkVector& v0 = s0.fV;
   *         const SkVector& v1 = s1.fV;
   *
   *         SkScalar denom = v0.cross(v1);
   *         if (SkScalarNearlyZero(denom, kCrossTolerance)) {
   *             // segments are parallel
   *             return SK_ScalarMax;
   *         }
   *
   *         SkVector w = s1.fP0 - s0.fP0;
   *         SkScalar localS = w.cross(v1) / denom;
   *         if (localS < 0) {
   *             localS = -localS;
   *         } else {
   *             localS -= SK_Scalar1;
   *         }
   *
   *         localS *= SkScalarAbs(localS);
   *         localS *= v0.dot(v0);
   *
   *         return localS;
   *     }
   * ```
   */
  public fun computeCrossingDistance(that: OffsetEdge?): SkScalar {
    TODO("Implement computeCrossingDistance")
  }
}
