package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkRectPriv {
 * public:
 *     // Returns an irect that is very large, and can be safely round-trip with SkRect and still
 *     // be considered non-empty (i.e. width/height > 0) even if we round-out the SkRect.
 *     static SkIRect MakeILarge() {
 *         // SK_MaxS32 >> 1 seemed better, but it did not survive round-trip with SkRect and rounding.
 *         // Also, 1 << 29 can be perfectly represented in float, while SK_MaxS32 >> 1 cannot.
 *         const int32_t large = 1 << 29;
 *         return { -large, -large, large, large };
 *     }
 *
 *     static SkIRect MakeILargestInverted() {
 *         return { SK_MaxS32, SK_MaxS32, SK_MinS32, SK_MinS32 };
 *     }
 *
 *     static SkRect MakeLargeS32() {
 *         SkRect r;
 *         r.set(MakeILarge());
 *         return r;
 *     }
 *
 *     static SkRect MakeLargest() {
 *         return { SK_ScalarMin, SK_ScalarMin, SK_ScalarMax, SK_ScalarMax };
 *     }
 *
 *     static constexpr SkRect MakeLargestInverted() {
 *         return { SK_ScalarMax, SK_ScalarMax, SK_ScalarMin, SK_ScalarMin };
 *     }
 *
 *     static void GrowToInclude(SkRect* r, const SkPoint& pt) {
 *         r->fLeft  =  std::min(pt.fX, r->fLeft);
 *         r->fRight =  std::max(pt.fX, r->fRight);
 *         r->fTop    = std::min(pt.fY, r->fTop);
 *         r->fBottom = std::max(pt.fY, r->fBottom);
 *     }
 *
 *     // Conservative check if r can be expressed in fixed-point.
 *     // Will return false for very large values that might have fit
 *     static bool FitsInFixed(const SkRect& r) {
 *         return SkFitsInFixed(r.fLeft) && SkFitsInFixed(r.fTop) &&
 *                SkFitsInFixed(r.fRight) && SkFitsInFixed(r.fBottom);
 *     }
 *
 *     // Returns r.width()/2 but divides first to avoid width() overflowing.
 *     static constexpr float HalfWidth(const SkRect& r) {
 *         return sk_float_midpoint(-r.fLeft, r.fRight);
 *     }
 *     // Returns r.height()/2 but divides first to avoid height() overflowing.
 *     static constexpr float HalfHeight(const SkRect& r) {
 *         return sk_float_midpoint(-r.fTop, r.fBottom);
 *     }
 *
 *     // Evaluate A-B. If the difference shape cannot be represented as a rectangle then false is
 *     // returned and 'out' is set to the largest rectangle contained in said shape. If true is
 *     // returned then A-B is representable as a rectangle, which is stored in 'out'.
 *     static bool Subtract(const SkRect& a, const SkRect& b, SkRect* out);
 *     static bool Subtract(const SkIRect& a, const SkIRect& b, SkIRect* out);
 *
 *     // Evaluate A-B, and return the largest rectangle contained in that shape (since the difference
 *     // may not be representable as rectangle). The returned rectangle will not intersect B.
 *     static SkRect Subtract(const SkRect& a, const SkRect& b) {
 *         SkRect diff;
 *         Subtract(a, b, &diff);
 *         return diff;
 *     }
 *     static SkIRect Subtract(const SkIRect& a, const SkIRect& b) {
 *         SkIRect diff;
 *         Subtract(a, b, &diff);
 *         return diff;
 *     }
 *
 *     // Returns true if the quadrilateral formed by transforming the four corners of 'a' contains 'b'
 *     // 'tol' is in the same coordinate space as 'b', to treat 'b' as 'tol' units inset.
 *     static bool QuadContainsRect(const SkMatrix& m,
 *                                  const SkIRect& a,
 *                                  const SkIRect& b,
 *                                  float tol=0.f);
 *     static bool QuadContainsRect(const SkM44& m, const SkRect& a, const SkRect& b, float tol=0.f);
 *     // Like QuadContainsRect() but returns the edge test masks ordered T, R, B, L.
 *     static skvx::int4 QuadContainsRectMask(const SkM44& m, const SkRect& a, const SkRect& b,
 *                                            float tol=0.f);
 *
 *     // Assuming 'src' does not intersect 'dst', returns the edge or corner of 'src' that is closest
 *     // to 'dst', e.g. the pixels that would be sampled from 'src' when clamp-tiled into 'dst'.
 *     //
 *     // The returned rectangle will not be empty if 'src' is not empty and 'dst' is not empty.
 *     // At least one of its width or height will be equal to 1 (possibly both if a corner is closest)
 *     //
 *     // Returns src.intersect(dst) if they do actually intersect.
 *     static SkIRect ClosestDisjointEdge(const SkIRect& src, const SkIRect& dst);
 * }
 * ```
 */
public abstract class SkRectPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkIRect MakeILarge() {
     *         // SK_MaxS32 >> 1 seemed better, but it did not survive round-trip with SkRect and rounding.
     *         // Also, 1 << 29 can be perfectly represented in float, while SK_MaxS32 >> 1 cannot.
     *         const int32_t large = 1 << 29;
     *         return { -large, -large, large, large };
     *     }
     * ```
     */
    public fun makeILarge(): SkIRect {
      TODO("Implement makeILarge")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkIRect MakeILargestInverted() {
     *         return { SK_MaxS32, SK_MaxS32, SK_MinS32, SK_MinS32 };
     *     }
     * ```
     */
    public fun makeILargestInverted(): SkIRect {
      TODO("Implement makeILargestInverted")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect MakeLargeS32() {
     *         SkRect r;
     *         r.set(MakeILarge());
     *         return r;
     *     }
     * ```
     */
    public fun makeLargeS32(): SkRect {
      TODO("Implement makeLargeS32")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect MakeLargest() {
     *         return { SK_ScalarMin, SK_ScalarMin, SK_ScalarMax, SK_ScalarMax };
     *     }
     * ```
     */
    public fun makeLargest(): SkRect {
      TODO("Implement makeLargest")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeLargestInverted() {
     *         return { SK_ScalarMax, SK_ScalarMax, SK_ScalarMin, SK_ScalarMin };
     *     }
     * ```
     */
    public fun makeLargestInverted(): SkRect {
      TODO("Implement makeLargestInverted")
    }

    /**
     * C++ original:
     * ```cpp
     * static void GrowToInclude(SkRect* r, const SkPoint& pt) {
     *         r->fLeft  =  std::min(pt.fX, r->fLeft);
     *         r->fRight =  std::max(pt.fX, r->fRight);
     *         r->fTop    = std::min(pt.fY, r->fTop);
     *         r->fBottom = std::max(pt.fY, r->fBottom);
     *     }
     * ```
     */
    public fun growToInclude(r: SkRect?, pt: SkPoint) {
      TODO("Implement growToInclude")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool FitsInFixed(const SkRect& r) {
     *         return SkFitsInFixed(r.fLeft) && SkFitsInFixed(r.fTop) &&
     *                SkFitsInFixed(r.fRight) && SkFitsInFixed(r.fBottom);
     *     }
     * ```
     */
    public fun fitsInFixed(r: SkRect): Boolean {
      TODO("Implement fitsInFixed")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr float HalfWidth(const SkRect& r) {
     *         return sk_float_midpoint(-r.fLeft, r.fRight);
     *     }
     * ```
     */
    public fun halfWidth(r: SkRect): Float {
      TODO("Implement halfWidth")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr float HalfHeight(const SkRect& r) {
     *         return sk_float_midpoint(-r.fTop, r.fBottom);
     *     }
     * ```
     */
    public fun halfHeight(r: SkRect): Float {
      TODO("Implement halfHeight")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRectPriv::Subtract(const SkRect& a, const SkRect& b, SkRect* out) {
     *     return subtract<SkRect>(a, b, out);
     * }
     * ```
     */
    public fun subtract(
      a: SkRect,
      b: SkRect,
      `out`: SkRect?,
    ): Boolean {
      TODO("Implement subtract")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRectPriv::Subtract(const SkIRect& a, const SkIRect& b, SkIRect* out) {
     *     return subtract<SkIRect>(a, b, out);
     * }
     * ```
     */
    public fun subtract(
      a: SkIRect,
      b: SkIRect,
      `out`: SkIRect?,
    ): Boolean {
      TODO("Implement subtract")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect Subtract(const SkRect& a, const SkRect& b) {
     *         SkRect diff;
     *         Subtract(a, b, &diff);
     *         return diff;
     *     }
     * ```
     */
    public fun subtract(a: SkRect, b: SkRect): SkRect {
      TODO("Implement subtract")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkIRect Subtract(const SkIRect& a, const SkIRect& b) {
     *         SkIRect diff;
     *         Subtract(a, b, &diff);
     *         return diff;
     *     }
     * ```
     */
    public fun subtract(a: SkIRect, b: SkIRect): SkIRect {
      TODO("Implement subtract")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRectPriv::QuadContainsRect(const SkMatrix& m,
     *                                   const SkIRect& a,
     *                                   const SkIRect& b,
     *                                   float tol) {
     *     return QuadContainsRect(SkM44(m), SkRect::Make(a), SkRect::Make(b), tol);
     * }
     * ```
     */
    public fun quadContainsRect(
      m: SkMatrix,
      a: SkIRect,
      b: SkIRect,
      tol: Float = 0.f,
    ): Boolean {
      TODO("Implement quadContainsRect")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRectPriv::QuadContainsRect(const SkM44& m, const SkRect& a, const SkRect& b, float tol) {
     *     return all(QuadContainsRectMask(m, a, b, tol));
     * }
     * ```
     */
    public fun quadContainsRect(
      m: SkM44,
      a: SkRect,
      b: SkRect,
      tol: Float = 0.f,
    ): Boolean {
      TODO("Implement quadContainsRect")
    }

    /**
     * C++ original:
     * ```cpp
     * skvx::int4 SkRectPriv::QuadContainsRectMask(const SkM44& m,
     *                                             const SkRect& a,
     *                                             const SkRect& b,
     *                                             float tol) {
     *     SkDEBUGCODE(SkM44 inverse;)
     *     SkASSERT(m.invert(&inverse));
     *     // With empty rectangles, the calculated edges could give surprising results. If 'a' were not
     *     // sorted, its normals would point outside the sorted rectangle, so lots of potential rects
     *     // would be seen as "contained". If 'a' is all 0s, its edge equations are also (0,0,0) so every
     *     // point has a distance of 0, and would be interpreted as inside.
     *     if (a.isEmpty()) {
     *         return skvx::int4(0); // all "false"
     *     }
     *     // However, 'b' is only used to define its 4 corners to check against the transformed edges.
     *     // This is valid regardless of b's emptiness or sortedness.
     *
     *     // Calculate the 4 homogenous coordinates of 'a' transformed by 'm' where Z=0 and W=1.
     *     auto ax = skvx::float4{a.fLeft, a.fRight, a.fRight, a.fLeft};
     *     auto ay = skvx::float4{a.fTop, a.fTop, a.fBottom, a.fBottom};
     *
     *     auto max = m.rc(0,0)*ax + m.rc(0,1)*ay + m.rc(0,3);
     *     auto may = m.rc(1,0)*ax + m.rc(1,1)*ay + m.rc(1,3);
     *     auto maw = m.rc(3,0)*ax + m.rc(3,1)*ay + m.rc(3,3);
     *
     *     if (all(maw < 0.f)) {
     *         // If all points of A are mapped to w < 0, then the edge equations end up representing the
     *         // convex hull of projected points when A should in fact be considered empty.
     *         return skvx::int4(0); // all "false"
     *     }
     *
     *     // Cross product of adjacent vertices provides homogenous lines for the 4 sides of the quad
     *     auto lA = may*skvx::shuffle<1,2,3,0>(maw) - maw*skvx::shuffle<1,2,3,0>(may);
     *     auto lB = maw*skvx::shuffle<1,2,3,0>(max) - max*skvx::shuffle<1,2,3,0>(maw);
     *     auto lC = max*skvx::shuffle<1,2,3,0>(may) - may*skvx::shuffle<1,2,3,0>(max);
     *
     *     // Before transforming, the corners of 'a' were in CW order, but afterwards they may become CCW,
     *     // so the sign corrects the direction of the edge normals to point inwards.
     *     float sign = (lA[0]*lB[1] - lB[0]*lA[1]) < 0 ? -1.f : 1.f;
     *
     *     // Calculate distance from 'b' to each edge. Since 'b' has presumably been transformed by 'm'
     *     // *and* projected, this assumes W = 1.
     *     SkRect bInset = b.makeInset(tol, tol);
     *     auto d0 = sign * (lA*bInset.fLeft  + lB*bInset.fTop    + lC);
     *     auto d1 = sign * (lA*bInset.fRight + lB*bInset.fTop    + lC);
     *     auto d2 = sign * (lA*bInset.fRight + lB*bInset.fBottom + lC);
     *     auto d3 = sign * (lA*bInset.fLeft  + lB*bInset.fBottom + lC);
     *
     *     // 'b' is contained in the mapped rectangle if all distances are >= 0
     *     return (d0 >= 0.f) & (d1 >= 0.f) & (d2 >= 0.f) & (d3 >= 0.f);
     * }
     * ```
     */
    public fun quadContainsRectMask(
      m: SkM44,
      a: SkRect,
      b: SkRect,
      tol: Float = 0.f,
    ): Int4 {
      TODO("Implement quadContainsRectMask")
    }

    /**
     * C++ original:
     * ```cpp
     * SkIRect SkRectPriv::ClosestDisjointEdge(const SkIRect& src, const SkIRect& dst) {
     *     if (src.isEmpty() || dst.isEmpty()) {
     *         return SkIRect::MakeEmpty();
     *     }
     *
     *     int l = src.fLeft;
     *     int r = src.fRight;
     *     if (r <= dst.fLeft) {
     *         // Select right column of pixels in crop
     *         l = r - 1;
     *     } else if (l >= dst.fRight) {
     *         // Left column of 'crop'
     *         r = l + 1;
     *     } else {
     *         // Regular intersection along X axis.
     *         l = SkTPin(l, dst.fLeft, dst.fRight);
     *         r = SkTPin(r, dst.fLeft, dst.fRight);
     *     }
     *
     *     int t = src.fTop;
     *     int b = src.fBottom;
     *     if (b <= dst.fTop) {
     *         // Select bottom row of pixels in crop
     *         t = b - 1;
     *     } else if (t >= dst.fBottom) {
     *         // Top row of 'crop'
     *         b = t + 1;
     *     } else {
     *         t = SkTPin(t, dst.fTop, dst.fBottom);
     *         b = SkTPin(b, dst.fTop, dst.fBottom);
     *     }
     *
     *     return SkIRect::MakeLTRB(l,t,r,b);
     * }
     * ```
     */
    public fun closestDisjointEdge(src: SkIRect, dst: SkIRect): SkIRect {
      TODO("Implement closestDisjointEdge")
    }
  }
}
