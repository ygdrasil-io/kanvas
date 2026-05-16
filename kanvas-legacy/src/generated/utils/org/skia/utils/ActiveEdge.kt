package org.skia.utils

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UShort
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct ActiveEdge {
 *     ActiveEdge() : fChild{ nullptr, nullptr }, fAbove(nullptr), fBelow(nullptr), fRed(false) {}
 *     ActiveEdge(const SkPoint& p0, const SkVector& v, uint16_t index0, uint16_t index1)
 *         : fSegment({ p0, v })
 *         , fIndex0(index0)
 *         , fIndex1(index1)
 *         , fAbove(nullptr)
 *         , fBelow(nullptr)
 *         , fRed(true) {
 *         fChild[0] = nullptr;
 *         fChild[1] = nullptr;
 *     }
 *
 *     // Returns true if "this" is above "that", assuming this->p0 is to the left of that->p0
 *     // This is only used to verify the edgelist -- the actual test for insertion/deletion is much
 *     // simpler because we can make certain assumptions then.
 *     bool aboveIfLeft(const ActiveEdge* that) const {
 *         const SkPoint& p0 = this->fSegment.fP0;
 *         const SkPoint& q0 = that->fSegment.fP0;
 *         SkASSERT(p0.fX <= q0.fX);
 *         SkVector d = q0 - p0;
 *         const SkVector& v = this->fSegment.fV;
 *         const SkVector& w = that->fSegment.fV;
 *         // The idea here is that if the vector between the origins of the two segments (d)
 *         // rotates counterclockwise up to the vector representing the "this" segment (v),
 *         // then we know that "this" is above "that". If the result is clockwise we say it's below.
 *         if (this->fIndex0 != that->fIndex0) {
 *             SkScalar cross = d.cross(v);
 *             if (cross > kCrossTolerance) {
 *                 return true;
 *             } else if (cross < -kCrossTolerance) {
 *                 return false;
 *             }
 *         } else if (this->fIndex1 == that->fIndex1) {
 *             return false;
 *         }
 *         // At this point either the two origins are nearly equal or the origin of "that"
 *         // lies on dv. So then we try the same for the vector from the tail of "this"
 *         // to the head of "that". Again, ccw means "this" is above "that".
 *         // d = that.P1 - this.P0
 *         //   = that.fP0 + that.fV - this.fP0
 *         //   = that.fP0 - this.fP0 + that.fV
 *         //   = old_d + that.fV
 *         d += w;
 *         SkScalar cross = d.cross(v);
 *         if (cross > kCrossTolerance) {
 *             return true;
 *         } else if (cross < -kCrossTolerance) {
 *             return false;
 *         }
 *         // If the previous check fails, the two segments are nearly collinear
 *         // First check y-coord of first endpoints
 *         if (p0.fX < q0.fX) {
 *             return (p0.fY >= q0.fY);
 *         } else if (p0.fY > q0.fY) {
 *             return true;
 *         } else if (p0.fY < q0.fY) {
 *             return false;
 *         }
 *         // The first endpoints are the same, so check the other endpoint
 *         SkPoint p1 = p0 + v;
 *         SkPoint q1 = q0 + w;
 *         if (p1.fX < q1.fX) {
 *             return (p1.fY >= q1.fY);
 *         } else {
 *             return (p1.fY > q1.fY);
 *         }
 *     }
 *
 *     // same as leftAndAbove(), but generalized
 *     bool above(const ActiveEdge* that) const {
 *         const SkPoint& p0 = this->fSegment.fP0;
 *         const SkPoint& q0 = that->fSegment.fP0;
 *         if (right(p0, q0)) {
 *             return !that->aboveIfLeft(this);
 *         } else {
 *             return this->aboveIfLeft(that);
 *         }
 *     }
 *
 *     bool intersect(const SkPoint& q0, const SkVector& w, uint16_t index0, uint16_t index1) const {
 *         // check first to see if these edges are neighbors in the polygon
 *         if (this->fIndex0 == index0 || this->fIndex1 == index0 ||
 *             this->fIndex0 == index1 || this->fIndex1 == index1) {
 *             return false;
 *         }
 *
 *         // We don't need the exact intersection point so we can do a simpler test here.
 *         const SkPoint& p0 = this->fSegment.fP0;
 *         const SkVector& v = this->fSegment.fV;
 *         SkPoint p1 = p0 + v;
 *         SkPoint q1 = q0 + w;
 *
 *         // We assume some x-overlap due to how the edgelist works
 *         // This allows us to simplify our test
 *         // We need some slop here because storing the vector and recomputing the second endpoint
 *         // doesn't necessary give us the original result in floating point.
 *         // TODO: Store vector as double? Store endpoint as well?
 *         SkASSERT(q0.fX <= p1.fX + SK_ScalarNearlyZero);
 *
 *         // if each segment straddles the other (i.e., the endpoints have different sides)
 *         // then they intersect
 *         bool result;
 *         if (p0.fX < q0.fX) {
 *             if (q1.fX < p1.fX) {
 *                 result = (compute_side(p0, v, q0)*compute_side(p0, v, q1) < 0);
 *             } else {
 *                 result = (compute_side(p0, v, q0)*compute_side(q0, w, p1) > 0);
 *             }
 *         } else {
 *             if (p1.fX < q1.fX) {
 *                 result = (compute_side(q0, w, p0)*compute_side(q0, w, p1) < 0);
 *             } else {
 *                 result = (compute_side(q0, w, p0)*compute_side(p0, v, q1) > 0);
 *             }
 *         }
 *         return result;
 *     }
 *
 *     bool intersect(const ActiveEdge* edge) {
 *         return this->intersect(edge->fSegment.fP0, edge->fSegment.fV, edge->fIndex0, edge->fIndex1);
 *     }
 *
 *     bool lessThan(const ActiveEdge* that) const {
 *         SkASSERT(!this->above(this));
 *         SkASSERT(!that->above(that));
 *         SkASSERT(!(this->above(that) && that->above(this)));
 *         return this->above(that);
 *     }
 *
 *     bool equals(uint16_t index0, uint16_t index1) const {
 *         return (this->fIndex0 == index0 && this->fIndex1 == index1);
 *     }
 *
 *     OffsetSegment fSegment;
 *     uint16_t fIndex0;   // indices for previous and next vertex in polygon
 *     uint16_t fIndex1;
 *     ActiveEdge* fChild[2];
 *     ActiveEdge* fAbove;
 *     ActiveEdge* fBelow;
 *     int32_t  fRed;
 * }
 * ```
 */
public data class ActiveEdge public constructor(
  /**
   * C++ original:
   * ```cpp
   * OffsetSegment fSegment
   * ```
   */
  public var fSegment: OffsetSegment,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fIndex0
   * ```
   */
  public var fIndex0: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fIndex1
   * ```
   */
  public var fIndex1: UShort,
  /**
   * C++ original:
   * ```cpp
   * ActiveEdge* fChild[2]
   * ```
   */
  public var fChild: Int,
  /**
   * C++ original:
   * ```cpp
   * ActiveEdge* fAbove
   * ```
   */
  public var fAbove: ActiveEdge?,
  /**
   * C++ original:
   * ```cpp
   * ActiveEdge* fBelow
   * ```
   */
  public var fBelow: ActiveEdge?,
  /**
   * C++ original:
   * ```cpp
   * int32_t  fRed
   * ```
   */
  public var fRed: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool aboveIfLeft(const ActiveEdge* that) const {
   *         const SkPoint& p0 = this->fSegment.fP0;
   *         const SkPoint& q0 = that->fSegment.fP0;
   *         SkASSERT(p0.fX <= q0.fX);
   *         SkVector d = q0 - p0;
   *         const SkVector& v = this->fSegment.fV;
   *         const SkVector& w = that->fSegment.fV;
   *         // The idea here is that if the vector between the origins of the two segments (d)
   *         // rotates counterclockwise up to the vector representing the "this" segment (v),
   *         // then we know that "this" is above "that". If the result is clockwise we say it's below.
   *         if (this->fIndex0 != that->fIndex0) {
   *             SkScalar cross = d.cross(v);
   *             if (cross > kCrossTolerance) {
   *                 return true;
   *             } else if (cross < -kCrossTolerance) {
   *                 return false;
   *             }
   *         } else if (this->fIndex1 == that->fIndex1) {
   *             return false;
   *         }
   *         // At this point either the two origins are nearly equal or the origin of "that"
   *         // lies on dv. So then we try the same for the vector from the tail of "this"
   *         // to the head of "that". Again, ccw means "this" is above "that".
   *         // d = that.P1 - this.P0
   *         //   = that.fP0 + that.fV - this.fP0
   *         //   = that.fP0 - this.fP0 + that.fV
   *         //   = old_d + that.fV
   *         d += w;
   *         SkScalar cross = d.cross(v);
   *         if (cross > kCrossTolerance) {
   *             return true;
   *         } else if (cross < -kCrossTolerance) {
   *             return false;
   *         }
   *         // If the previous check fails, the two segments are nearly collinear
   *         // First check y-coord of first endpoints
   *         if (p0.fX < q0.fX) {
   *             return (p0.fY >= q0.fY);
   *         } else if (p0.fY > q0.fY) {
   *             return true;
   *         } else if (p0.fY < q0.fY) {
   *             return false;
   *         }
   *         // The first endpoints are the same, so check the other endpoint
   *         SkPoint p1 = p0 + v;
   *         SkPoint q1 = q0 + w;
   *         if (p1.fX < q1.fX) {
   *             return (p1.fY >= q1.fY);
   *         } else {
   *             return (p1.fY > q1.fY);
   *         }
   *     }
   * ```
   */
  public fun aboveIfLeft(that: ActiveEdge?): Boolean {
    TODO("Implement aboveIfLeft")
  }

  /**
   * C++ original:
   * ```cpp
   * bool above(const ActiveEdge* that) const {
   *         const SkPoint& p0 = this->fSegment.fP0;
   *         const SkPoint& q0 = that->fSegment.fP0;
   *         if (right(p0, q0)) {
   *             return !that->aboveIfLeft(this);
   *         } else {
   *             return this->aboveIfLeft(that);
   *         }
   *     }
   * ```
   */
  public fun above(that: ActiveEdge?): Boolean {
    TODO("Implement above")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersect(const SkPoint& q0, const SkVector& w, uint16_t index0, uint16_t index1) const {
   *         // check first to see if these edges are neighbors in the polygon
   *         if (this->fIndex0 == index0 || this->fIndex1 == index0 ||
   *             this->fIndex0 == index1 || this->fIndex1 == index1) {
   *             return false;
   *         }
   *
   *         // We don't need the exact intersection point so we can do a simpler test here.
   *         const SkPoint& p0 = this->fSegment.fP0;
   *         const SkVector& v = this->fSegment.fV;
   *         SkPoint p1 = p0 + v;
   *         SkPoint q1 = q0 + w;
   *
   *         // We assume some x-overlap due to how the edgelist works
   *         // This allows us to simplify our test
   *         // We need some slop here because storing the vector and recomputing the second endpoint
   *         // doesn't necessary give us the original result in floating point.
   *         // TODO: Store vector as double? Store endpoint as well?
   *         SkASSERT(q0.fX <= p1.fX + SK_ScalarNearlyZero);
   *
   *         // if each segment straddles the other (i.e., the endpoints have different sides)
   *         // then they intersect
   *         bool result;
   *         if (p0.fX < q0.fX) {
   *             if (q1.fX < p1.fX) {
   *                 result = (compute_side(p0, v, q0)*compute_side(p0, v, q1) < 0);
   *             } else {
   *                 result = (compute_side(p0, v, q0)*compute_side(q0, w, p1) > 0);
   *             }
   *         } else {
   *             if (p1.fX < q1.fX) {
   *                 result = (compute_side(q0, w, p0)*compute_side(q0, w, p1) < 0);
   *             } else {
   *                 result = (compute_side(q0, w, p0)*compute_side(p0, v, q1) > 0);
   *             }
   *         }
   *         return result;
   *     }
   * ```
   */
  public fun intersect(
    q0: SkPoint,
    w: SkVector,
    index0: UShort,
    index1: UShort,
  ): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersect(const ActiveEdge* edge) {
   *         return this->intersect(edge->fSegment.fP0, edge->fSegment.fV, edge->fIndex0, edge->fIndex1);
   *     }
   * ```
   */
  public fun intersect(edge: ActiveEdge?): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool lessThan(const ActiveEdge* that) const {
   *         SkASSERT(!this->above(this));
   *         SkASSERT(!that->above(that));
   *         SkASSERT(!(this->above(that) && that->above(this)));
   *         return this->above(that);
   *     }
   * ```
   */
  public fun lessThan(that: ActiveEdge?): Boolean {
    TODO("Implement lessThan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equals(uint16_t index0, uint16_t index1) const {
   *         return (this->fIndex0 == index0 && this->fIndex1 == index1);
   *     }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
