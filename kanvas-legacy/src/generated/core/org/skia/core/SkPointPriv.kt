package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.ULong
import org.skia.math.SkIPoint
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkPointPriv {
 * public:
 *     enum Side {
 *         kLeft_Side  = -1,
 *         kOn_Side    =  0,
 *         kRight_Side =  1,
 *     };
 *
 *     static bool AreFinite(const SkPoint array[], int count) {
 *         return SkIsFinite(&array[0].fX, count << 1);
 *     }
 *
 *     static const SkScalar* AsScalars(const SkPoint& pt) { return &pt.fX; }
 *
 *     static bool CanNormalize(SkScalar dx, SkScalar dy) {
 *         return SkIsFinite(dx, dy) && (dx || dy);
 *     }
 *
 *     static SkScalar DistanceToLineBetweenSqd(const SkPoint& pt, const SkPoint& a,
 *                                              const SkPoint& b, Side* side = nullptr);
 *
 *     static SkScalar DistanceToLineBetween(const SkPoint& pt, const SkPoint& a,
 *                                           const SkPoint& b, Side* side = nullptr) {
 *         return SkScalarSqrt(DistanceToLineBetweenSqd(pt, a, b, side));
 *     }
 *
 *     static SkScalar DistanceToLineSegmentBetweenSqd(const SkPoint& pt, const SkPoint& a,
 *                                                    const SkPoint& b);
 *
 *     static SkScalar DistanceToLineSegmentBetween(const SkPoint& pt, const SkPoint& a,
 *                                                  const SkPoint& b) {
 *         return SkScalarSqrt(DistanceToLineSegmentBetweenSqd(pt, a, b));
 *     }
 *
 *     static SkScalar DistanceToSqd(const SkPoint& pt, const SkPoint& a) {
 *         SkScalar dx = pt.fX - a.fX;
 *         SkScalar dy = pt.fY - a.fY;
 *         return dx * dx + dy * dy;
 *     }
 *
 *     static bool EqualsWithinTolerance(const SkPoint& p1, const SkPoint& p2) {
 *         return !CanNormalize(p1.fX - p2.fX, p1.fY - p2.fY);
 *     }
 *
 *     static bool EqualsWithinTolerance(const SkPoint& pt, const SkPoint& p, SkScalar tol) {
 *         return SkScalarNearlyZero(pt.fX - p.fX, tol)
 *                && SkScalarNearlyZero(pt.fY - p.fY, tol);
 *     }
 *
 *     static SkScalar LengthSqd(const SkPoint& pt) {
 *         return SkPoint::DotProduct(pt, pt);
 *     }
 *
 *     static void Negate(SkIPoint& pt) {
 *         pt.fX = -pt.fX;
 *         pt.fY = -pt.fY;
 *     }
 *
 *     static void RotateCCW(const SkPoint& src, SkPoint* dst) {
 *         // use a tmp in case src == dst
 *         SkScalar tmp = src.fX;
 *         dst->fX = src.fY;
 *         dst->fY = -tmp;
 *     }
 *
 *     static void RotateCCW(SkPoint* pt) {
 *         RotateCCW(*pt, pt);
 *     }
 *
 *     static void RotateCW(const SkPoint& src, SkPoint* dst) {
 *         // use a tmp in case src == dst
 *         SkScalar tmp = src.fX;
 *         dst->fX = -src.fY;
 *         dst->fY = tmp;
 *     }
 *
 *     static void RotateCW(SkPoint* pt) {
 *         RotateCW(*pt, pt);
 *     }
 *
 *     static bool SetLengthFast(SkPoint* pt, float length);
 *
 *     static SkPoint MakeOrthog(const SkPoint& vec, Side side = kLeft_Side) {
 *         SkASSERT(side == kRight_Side || side == kLeft_Side);
 *         return (side == kRight_Side) ? SkPoint{-vec.fY, vec.fX} : SkPoint{vec.fY, -vec.fX};
 *     }
 *
 *     // counter-clockwise fan
 *     static void SetRectFan(SkPoint v[], SkScalar l, SkScalar t, SkScalar r, SkScalar b,
 *             size_t stride) {
 *         SkASSERT(stride >= sizeof(SkPoint));
 *
 *         ((SkPoint*)((intptr_t)v + 0 * stride))->set(l, t);
 *         ((SkPoint*)((intptr_t)v + 1 * stride))->set(l, b);
 *         ((SkPoint*)((intptr_t)v + 2 * stride))->set(r, b);
 *         ((SkPoint*)((intptr_t)v + 3 * stride))->set(r, t);
 *     }
 *
 *     // tri strip with two counter-clockwise triangles
 *     static void SetRectTriStrip(SkPoint v[], SkScalar l, SkScalar t, SkScalar r, SkScalar b,
 *             size_t stride) {
 *         SkASSERT(stride >= sizeof(SkPoint));
 *
 *         ((SkPoint*)((intptr_t)v + 0 * stride))->set(l, t);
 *         ((SkPoint*)((intptr_t)v + 1 * stride))->set(l, b);
 *         ((SkPoint*)((intptr_t)v + 2 * stride))->set(r, t);
 *         ((SkPoint*)((intptr_t)v + 3 * stride))->set(r, b);
 *     }
 *     static void SetRectTriStrip(SkPoint v[], const SkRect& rect, size_t stride) {
 *         SetRectTriStrip(v, rect.fLeft, rect.fTop, rect.fRight, rect.fBottom, stride);
 *     }
 * }
 * ```
 */
public open class SkPointPriv {
  public enum class Side {
    kLeft_Side,
    kOn_Side,
    kRight_Side,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool AreFinite(const SkPoint array[], int count) {
     *         return SkIsFinite(&array[0].fX, count << 1);
     *     }
     * ```
     */
    public fun areFinite(array: Array<SkPoint>, count: Int): Boolean {
      TODO("Implement areFinite")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkScalar* AsScalars(const SkPoint& pt) { return &pt.fX; }
     * ```
     */
    public fun asScalars(pt: SkPoint): SkScalar {
      TODO("Implement asScalars")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool CanNormalize(SkScalar dx, SkScalar dy) {
     *         return SkIsFinite(dx, dy) && (dx || dy);
     *     }
     * ```
     */
    public fun canNormalize(dx: SkScalar, dy: SkScalar): Boolean {
      TODO("Implement canNormalize")
    }

    /**
     * C++ original:
     * ```cpp
     * float SkPointPriv::DistanceToLineBetweenSqd(const SkPoint& pt, const SkPoint& a,
     *                                                const SkPoint& b,
     *                                                Side* side) {
     *
     *     SkVector u = b - a;
     *     SkVector v = pt - a;
     *
     *     float uLengthSqd = LengthSqd(u);
     *     float det = u.cross(v);
     *     if (side) {
     *         SkASSERT(-1 == kLeft_Side &&
     *                   0 == kOn_Side &&
     *                   1 == kRight_Side);
     *         *side = (Side)sk_float_sgn(det);
     *     }
     *     float temp = sk_ieee_float_divide(det, uLengthSqd);
     *     temp *= det;
     *     // It's possible we have a degenerate line vector, or we're so far away it looks degenerate
     *     // In this case, return squared distance to point A.
     *     if (!SkIsFinite(temp)) {
     *         return LengthSqd(v);
     *     }
     *     return temp;
     * }
     * ```
     */
    public fun distanceToLineBetweenSqd(
      pt: SkPoint,
      a: SkPoint,
      b: SkPoint,
      side: Side? = TODO(),
    ): SkScalar {
      TODO("Implement distanceToLineBetweenSqd")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar DistanceToLineBetween(const SkPoint& pt, const SkPoint& a,
     *                                           const SkPoint& b, Side* side = nullptr) {
     *         return SkScalarSqrt(DistanceToLineBetweenSqd(pt, a, b, side));
     *     }
     * ```
     */
    public fun distanceToLineBetween(
      pt: SkPoint,
      a: SkPoint,
      b: SkPoint,
      side: Side? = TODO(),
    ): SkScalar {
      TODO("Implement distanceToLineBetween")
    }

    /**
     * C++ original:
     * ```cpp
     * float SkPointPriv::DistanceToLineSegmentBetweenSqd(const SkPoint& pt, const SkPoint& a,
     *                                                       const SkPoint& b) {
     *     // See comments to distanceToLineBetweenSqd. If the projection of c onto
     *     // u is between a and b then this returns the same result as that
     *     // function. Otherwise, it returns the distance to the closest of a and
     *     // b. Let the projection of v onto u be v'.  There are three cases:
     *     //    1. v' points opposite to u. c is not between a and b and is closer
     *     //       to a than b.
     *     //    2. v' points along u and has magnitude less than y. c is between
     *     //       a and b and the distance to the segment is the same as distance
     *     //       to the line ab.
     *     //    3. v' points along u and has greater magnitude than u. c is not
     *     //       between a and b and is closer to b than a.
     *     // v' = (u dot v) * u / |u|. So if (u dot v)/|u| is less than zero we're
     *     // in case 1. If (u dot v)/|u| is > |u| we are in case 3. Otherwise,
     *     // we're in case 2. We actually compare (u dot v) to 0 and |u|^2 to
     *     // avoid a sqrt to compute |u|.
     *
     *     SkVector u = b - a;
     *     SkVector v = pt - a;
     *
     *     float uLengthSqd = LengthSqd(u);
     *     float uDotV = SkPoint::DotProduct(u, v);
     *
     *     // closest point is point A
     *     if (uDotV <= 0) {
     *         return LengthSqd(v);
     *     // closest point is point B
     *     } else if (uDotV > uLengthSqd) {
     *         return DistanceToSqd(b, pt);
     *     // closest point is inside segment
     *     } else {
     *         float det = u.cross(v);
     *         float temp = sk_ieee_float_divide(det, uLengthSqd);
     *         temp *= det;
     *         // It's possible we have a degenerate segment, or we're so far away it looks degenerate
     *         // In this case, return squared distance to point A.
     *         if (!SkIsFinite(temp)) {
     *             return LengthSqd(v);
     *         }
     *         return temp;
     *     }
     * }
     * ```
     */
    public fun distanceToLineSegmentBetweenSqd(
      pt: SkPoint,
      a: SkPoint,
      b: SkPoint,
    ): SkScalar {
      TODO("Implement distanceToLineSegmentBetweenSqd")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar DistanceToLineSegmentBetween(const SkPoint& pt, const SkPoint& a,
     *                                                  const SkPoint& b) {
     *         return SkScalarSqrt(DistanceToLineSegmentBetweenSqd(pt, a, b));
     *     }
     * ```
     */
    public fun distanceToLineSegmentBetween(
      pt: SkPoint,
      a: SkPoint,
      b: SkPoint,
    ): SkScalar {
      TODO("Implement distanceToLineSegmentBetween")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar DistanceToSqd(const SkPoint& pt, const SkPoint& a) {
     *         SkScalar dx = pt.fX - a.fX;
     *         SkScalar dy = pt.fY - a.fY;
     *         return dx * dx + dy * dy;
     *     }
     * ```
     */
    public fun distanceToSqd(pt: SkPoint, a: SkPoint): SkScalar {
      TODO("Implement distanceToSqd")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool EqualsWithinTolerance(const SkPoint& p1, const SkPoint& p2) {
     *         return !CanNormalize(p1.fX - p2.fX, p1.fY - p2.fY);
     *     }
     * ```
     */
    public fun equalsWithinTolerance(p1: SkPoint, p2: SkPoint): Boolean {
      TODO("Implement equalsWithinTolerance")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool EqualsWithinTolerance(const SkPoint& pt, const SkPoint& p, SkScalar tol) {
     *         return SkScalarNearlyZero(pt.fX - p.fX, tol)
     *                && SkScalarNearlyZero(pt.fY - p.fY, tol);
     *     }
     * ```
     */
    public fun equalsWithinTolerance(
      pt: SkPoint,
      p: SkPoint,
      tol: SkScalar,
    ): Boolean {
      TODO("Implement equalsWithinTolerance")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar LengthSqd(const SkPoint& pt) {
     *         return SkPoint::DotProduct(pt, pt);
     *     }
     * ```
     */
    public fun lengthSqd(pt: SkPoint): SkScalar {
      TODO("Implement lengthSqd")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Negate(SkIPoint& pt) {
     *         pt.fX = -pt.fX;
     *         pt.fY = -pt.fY;
     *     }
     * ```
     */
    public fun negate(pt: SkIPoint) {
      TODO("Implement negate")
    }

    /**
     * C++ original:
     * ```cpp
     * static void RotateCCW(const SkPoint& src, SkPoint* dst) {
     *         // use a tmp in case src == dst
     *         SkScalar tmp = src.fX;
     *         dst->fX = src.fY;
     *         dst->fY = -tmp;
     *     }
     * ```
     */
    public fun rotateCCW(src: SkPoint, dst: SkPoint?) {
      TODO("Implement rotateCCW")
    }

    /**
     * C++ original:
     * ```cpp
     * static void RotateCCW(SkPoint* pt) {
     *         RotateCCW(*pt, pt);
     *     }
     * ```
     */
    public fun rotateCCW(pt: SkPoint?) {
      TODO("Implement rotateCCW")
    }

    /**
     * C++ original:
     * ```cpp
     * static void RotateCW(const SkPoint& src, SkPoint* dst) {
     *         // use a tmp in case src == dst
     *         SkScalar tmp = src.fX;
     *         dst->fX = -src.fY;
     *         dst->fY = tmp;
     *     }
     * ```
     */
    public fun rotateCW(src: SkPoint, dst: SkPoint?) {
      TODO("Implement rotateCW")
    }

    /**
     * C++ original:
     * ```cpp
     * static void RotateCW(SkPoint* pt) {
     *         RotateCW(*pt, pt);
     *     }
     * ```
     */
    public fun rotateCW(pt: SkPoint?) {
      TODO("Implement rotateCW")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPointPriv::SetLengthFast(SkPoint* pt, float length) {
     *     return set_point_length<true>(pt, pt->fX, pt->fY, length);
     * }
     * ```
     */
    public fun setLengthFast(pt: SkPoint?, length: Float): Boolean {
      TODO("Implement setLengthFast")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPoint MakeOrthog(const SkPoint& vec, Side side = kLeft_Side) {
     *         SkASSERT(side == kRight_Side || side == kLeft_Side);
     *         return (side == kRight_Side) ? SkPoint{-vec.fY, vec.fX} : SkPoint{vec.fY, -vec.fX};
     *     }
     * ```
     */
    public fun makeOrthog(vec: SkPoint, side: Side = TODO()): SkPoint {
      TODO("Implement makeOrthog")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetRectFan(SkPoint v[], SkScalar l, SkScalar t, SkScalar r, SkScalar b,
     *             size_t stride) {
     *         SkASSERT(stride >= sizeof(SkPoint));
     *
     *         ((SkPoint*)((intptr_t)v + 0 * stride))->set(l, t);
     *         ((SkPoint*)((intptr_t)v + 1 * stride))->set(l, b);
     *         ((SkPoint*)((intptr_t)v + 2 * stride))->set(r, b);
     *         ((SkPoint*)((intptr_t)v + 3 * stride))->set(r, t);
     *     }
     * ```
     */
    public fun setRectFan(
      v: Array<SkPoint>,
      l: SkScalar,
      t: SkScalar,
      r: SkScalar,
      b: SkScalar,
      stride: ULong,
    ) {
      TODO("Implement setRectFan")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetRectTriStrip(SkPoint v[], SkScalar l, SkScalar t, SkScalar r, SkScalar b,
     *             size_t stride) {
     *         SkASSERT(stride >= sizeof(SkPoint));
     *
     *         ((SkPoint*)((intptr_t)v + 0 * stride))->set(l, t);
     *         ((SkPoint*)((intptr_t)v + 1 * stride))->set(l, b);
     *         ((SkPoint*)((intptr_t)v + 2 * stride))->set(r, t);
     *         ((SkPoint*)((intptr_t)v + 3 * stride))->set(r, b);
     *     }
     * ```
     */
    public fun setRectTriStrip(
      v: Array<SkPoint>,
      l: SkScalar,
      t: SkScalar,
      r: SkScalar,
      b: SkScalar,
      stride: ULong,
    ) {
      TODO("Implement setRectTriStrip")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetRectTriStrip(SkPoint v[], const SkRect& rect, size_t stride) {
     *         SetRectTriStrip(v, rect.fLeft, rect.fTop, rect.fRight, rect.fBottom, stride);
     *     }
     * ```
     */
    public fun setRectTriStrip(
      v: Array<SkPoint>,
      rect: SkRect,
      stride: ULong,
    ) {
      TODO("Implement setRectTriStrip")
    }
  }
}
