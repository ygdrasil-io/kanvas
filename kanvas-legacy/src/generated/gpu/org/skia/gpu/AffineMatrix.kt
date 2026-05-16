package org.skia.gpu

import kotlin.Int
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class AffineMatrix {
 * public:
 *     AffineMatrix() = default;
 *     AffineMatrix(const SkMatrix& m) { *this = m; }
 *
 *     AffineMatrix& operator=(const SkMatrix& m) {
 *         SkASSERT(!m.hasPerspective());
 *         // Duplicate the matrix in float4.lo and float4.hi so we can map two points at once.
 *         fScale = skvx::float2(m.getScaleX(), m.getScaleY()).xyxy();
 *         fSkew = skvx::float2(m.getSkewX(), m.getSkewY()).xyxy();
 *         fTrans = skvx::float2(m.getTranslateX(), m.getTranslateY()).xyxy();
 *         return *this;
 *     }
 *
 *     SK_ALWAYS_INLINE skvx::float4 map2Points(skvx::float4 p0p1) const {
 *         return fScale * p0p1 + (fSkew * p0p1.yxwz() + fTrans);
 *     }
 *
 *     SK_ALWAYS_INLINE skvx::float4 map2Points(const SkPoint pts[2]) const {
 *         return this->map2Points(skvx::float4::Load(pts));
 *     }
 *
 *     SK_ALWAYS_INLINE skvx::float4 map2Points(SkPoint p0, SkPoint p1) const {
 *         return this->map2Points(skvx::float4(sk_bit_cast<skvx::float2>(p0),
 *                                              sk_bit_cast<skvx::float2>(p1)));
 *     }
 *
 *     SK_ALWAYS_INLINE skvx::float2 mapPoint(skvx::float2 p) const {
 *         return fScale.lo * p + (fSkew.lo * p.yx() + fTrans.lo);
 *     }
 *
 *     SK_ALWAYS_INLINE skvx::float2 map1Point(const SkPoint pt[1]) const {
 *         return this->mapPoint(skvx::float2::Load(pt));
 *     }
 *
 *     SK_ALWAYS_INLINE SkPoint mapPoint(SkPoint p) const {
 *         return sk_bit_cast<SkPoint>(this->mapPoint(sk_bit_cast<skvx::float2>(p)));
 *     }
 *
 * private:
 *     skvx::float4 fScale;
 *     skvx::float4 fSkew;
 *     skvx::float4 fTrans;
 * }
 * ```
 */
public data class AffineMatrix public constructor(
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fSkew
   * ```
   */
  private var fSkew: Int,
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fTrans
   * ```
   */
  private var fTrans: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * AffineMatrix& operator=(const SkMatrix& m) {
   *         SkASSERT(!m.hasPerspective());
   *         // Duplicate the matrix in float4.lo and float4.hi so we can map two points at once.
   *         fScale = skvx::float2(m.getScaleX(), m.getScaleY()).xyxy();
   *         fSkew = skvx::float2(m.getSkewX(), m.getSkewY()).xyxy();
   *         fTrans = skvx::float2(m.getTranslateX(), m.getTranslateY()).xyxy();
   *         return *this;
   *     }
   * ```
   */
  public fun assign(m: SkMatrix) {
    TODO("Implement assign")
  }
}
