package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkMatrixPriv {
 * public:
 *     enum {
 *         // writeTo/readFromMemory will never return a value larger than this
 *         kMaxFlattenSize = 9 * sizeof(SkScalar) + sizeof(uint32_t),
 *     };
 *
 *     static size_t WriteToMemory(const SkMatrix& matrix, void* buffer) {
 *         return matrix.writeToMemory(buffer);
 *     }
 *
 *     static size_t ReadFromMemory(SkMatrix* matrix, const void* buffer, size_t length) {
 *         return matrix->readFromMemory(buffer, length);
 *     }
 *
 *     typedef SkMatrix::MapPtsProc MapPtsProc;
 *
 *
 *     static MapPtsProc GetMapPtsProc(const SkMatrix& matrix) {
 *         return SkMatrix::GetMapPtsProc(matrix.getType());
 *     }
 *
 *     /**
 *      *  Attempt to map the rect through the inverse of the matrix. If it is not invertible,
 *      *  then this returns false and dst is unchanged.
 *      */
 *     [[nodiscard]] static bool InverseMapRect(const SkMatrix& mx, SkRect* dst, const SkRect& src) {
 *         if (mx.isScaleTranslate()) {
 *             // A scale-translate matrix with a 0 scale factor is not invertible.
 *             if (mx.getScaleX() == 0.f || mx.getScaleY() == 0.f) {
 *                 return false;
 *             }
 *
 *             const SkScalar tx = mx.getTranslateX();
 *             const SkScalar ty = mx.getTranslateY();
 *             // mx maps coordinates as ((sx*x + tx), (sy*y + ty)) so the inverse is
 *             // ((x - tx)/sx), (y - ty)/sy). If sx or sy are negative, we have to swap the edge
 *             // values to maintain a sorted rect.
 *             auto inverted = skvx::float4::Load(&src.fLeft);
 *             inverted -= skvx::float4(tx, ty, tx, ty);
 *
 *             if (mx.getType() > SkMatrix::kTranslate_Mask) {
 *                 const SkScalar sx = 1.f / mx.getScaleX();
 *                 const SkScalar sy = 1.f / mx.getScaleY();
 *                 inverted *= skvx::float4(sx, sy, sx, sy);
 *                 if (sx < 0.f && sy < 0.f) {
 *                     inverted = skvx::shuffle<2, 3, 0, 1>(inverted); // swap L|R and T|B
 *                 } else if (sx < 0.f) {
 *                     inverted = skvx::shuffle<2, 1, 0, 3>(inverted); // swap L|R
 *                 } else if (sy < 0.f) {
 *                     inverted = skvx::shuffle<0, 3, 2, 1>(inverted); // swap T|B
 *                 }
 *             }
 *             inverted.store(&dst->fLeft);
 *             return true;
 *         }
 *
 *         // general case
 *         if (auto inverse = mx.invert()) {
 *             inverse->mapRect(dst, src);
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /** Maps count pts, skipping stride bytes to advance from one SkPoint to the next.
 *         Points are mapped by multiplying each SkPoint by SkMatrix. Given:
 *
 *                      | A B C |        | x |
 *             Matrix = | D E F |,  pt = | y |
 *                      | G H I |        | 1 |
 *
 *         each resulting pts SkPoint is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         @param mx      matrix used to map the points
 *         @param pts     storage for mapped points
 *         @param stride  size of record starting with SkPoint, in bytes
 *         @param count   number of points to transform
 *     */
 *     static void MapPointsWithStride(const SkMatrix& mx, SkPoint pts[], size_t stride, int count) {
 *         SkASSERT(stride >= sizeof(SkPoint));
 *         SkASSERT(0 == stride % sizeof(SkScalar));
 *
 *         SkMatrix::TypeMask tm = mx.getType();
 *
 *         if (SkMatrix::kIdentity_Mask == tm) {
 *             return;
 *         }
 *         if (SkMatrix::kTranslate_Mask == tm) {
 *             const SkScalar tx = mx.getTranslateX();
 *             const SkScalar ty = mx.getTranslateY();
 *             skvx::float2 trans(tx, ty);
 *             for (int i = 0; i < count; ++i) {
 *                 (skvx::float2::Load(&pts->fX) + trans).store(&pts->fX);
 *                 pts = (SkPoint*)((intptr_t)pts + stride);
 *             }
 *             return;
 *         }
 *         // Insert other special-cases here (e.g. scale+translate)
 *
 *         // general case
 *         if (mx.hasPerspective()) {
 *             for (int i = 0; i < count; ++i) {
 *                 *pts = mx.mapPointPerspective(*pts);
 *                 pts = (SkPoint*)((intptr_t)pts + stride);
 *             }
 *         } else {
 *             for (int i = 0; i < count; ++i) {
 *                 *pts = mx.mapPointAffine(*pts);
 *                 pts = (SkPoint*)((intptr_t)pts + stride);
 *             }
 *         }
 *     }
 *
 *     /** Maps src SkPoint array of length count to dst SkPoint array, skipping stride bytes
 *         to advance from one SkPoint to the next.
 *         Points are mapped by multiplying each SkPoint by SkMatrix. Given:
 *
 *                      | A B C |         | x |
 *             Matrix = | D E F |,  src = | y |
 *                      | G H I |         | 1 |
 *
 *         each resulting dst SkPoint is computed as:
 *
 *                           |A B C| |x|                               Ax+By+C   Dx+Ey+F
 *             Matrix * pt = |D E F| |y| = |Ax+By+C Dx+Ey+F Gx+Hy+I| = ------- , -------
 *                           |G H I| |1|                               Gx+Hy+I   Gx+Hy+I
 *
 *         @param mx      matrix used to map the points
 *         @param dst     storage for mapped points
 *         @param src     points to transform
 *         @param stride  size of record starting with SkPoint, in bytes
 *         @param count   number of points to transform
 *     */
 *     static void MapPointsWithStride(const SkMatrix& mx, SkPoint dst[], size_t dstStride,
 *                                     const SkPoint src[], size_t srcStride, int count) {
 *         SkASSERT(srcStride >= sizeof(SkPoint));
 *         SkASSERT(dstStride >= sizeof(SkPoint));
 *         SkASSERT(0 == srcStride % sizeof(SkScalar));
 *         SkASSERT(0 == dstStride % sizeof(SkScalar));
 *         for (int i = 0; i < count; ++i) {
 *             *dst = mx.mapPoint(*src);
 *             src = (SkPoint*)((intptr_t)src + srcStride);
 *             dst = (SkPoint*)((intptr_t)dst + dstStride);
 *         }
 *     }
 *
 *     static void MapHomogeneousPointsWithStride(const SkMatrix& mx, SkPoint3 dst[], size_t dstStride,
 *                                                const SkPoint3 src[], size_t srcStride, int count);
 *
 *     static bool PostIDiv(SkMatrix* matrix, int divx, int divy) {
 *         return matrix->postIDiv(divx, divy);
 *     }
 *
 *     static bool CheapEqual(const SkMatrix& a, const SkMatrix& b) {
 *         return &a == &b || 0 == memcmp(a.fMat, b.fMat, sizeof(a.fMat));
 *     }
 *
 *     static const SkScalar* M44ColMajor(const SkM44& m) { return m.fMat; }
 *
 *     // This is legacy functionality that only checks the 3x3 portion. The matrix could have Z-based
 *     // shear, or other complex behavior. Only use this if you're planning to use the information
 *     // to accelerate some purely 2D operation.
 *     static bool IsScaleTranslateAsM33(const SkM44& m) {
 *         return m.rc(1,0) == 0 && m.rc(3,0) == 0 &&
 *                m.rc(0,1) == 0 && m.rc(3,1) == 0 &&
 *                m.rc(3,3) == 1;
 *
 *     }
 *
 *     // Map the four corners of 'r' and return the bounding box of those points. The four corners of
 *     // 'r' are assumed to have z = 0 and w = 1. If the matrix has perspective, the returned
 *     // rectangle will be the bounding box of the projected points after being clipped to w > 0.
 *     static SkRect MapRect(const SkM44& m, const SkRect& r);
 *
 *     // Returns the differential area scale factor for a local point 'p' that will be transformed
 *     // by 'm' (which may have perspective). If 'm' does not have perspective, this scale factor is
 *     // constant regardless of 'p'; when it does have perspective, it is specific to that point.
 *     //
 *     // This can be crudely thought of as "device pixel area" / "local pixel area" at 'p'.
 *     //
 *     // Returns positive infinity if the transformed homogeneous point has w <= 0.
 *     static SkScalar DifferentialAreaScale(const SkMatrix& m, const SkPoint& p);
 *
 *     // Determines if the transformation m applied to the bounds can be approximated by
 *     // an affine transformation, i.e., the perspective part of the transformation has little
 *     // visible effect.
 *     static bool NearlyAffine(const SkMatrix& m,
 *                              const SkRect& bounds,
 *                              SkScalar tolerance = SK_ScalarNearlyZero);
 *
 *     static SkScalar ComputeResScaleForStroking(const SkMatrix& matrix);
 * }
 * ```
 */
public open class SkMatrixPriv {
  public companion object {
    public val kMaxFlattenSize: Int = TODO("Initialize kMaxFlattenSize")

    /**
     * C++ original:
     * ```cpp
     * static size_t WriteToMemory(const SkMatrix& matrix, void* buffer) {
     *         return matrix.writeToMemory(buffer);
     *     }
     * ```
     */
    public fun writeToMemory(matrix: SkMatrix, buffer: Unit?): Int {
      TODO("Implement writeToMemory")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t ReadFromMemory(SkMatrix* matrix, const void* buffer, size_t length) {
     *         return matrix->readFromMemory(buffer, length);
     *     }
     * ```
     */
    public fun readFromMemory(
      matrix: SkMatrix?,
      buffer: Unit?,
      length: ULong,
    ): Int {
      TODO("Implement readFromMemory")
    }

    /**
     * C++ original:
     * ```cpp
     * static MapPtsProc GetMapPtsProc(const SkMatrix& matrix) {
     *         return SkMatrix::GetMapPtsProc(matrix.getType());
     *     }
     * ```
     */
    public fun getMapPtsProc(matrix: SkMatrix): SkMatrixPrivMapPtsProc {
      TODO("Implement getMapPtsProc")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool InverseMapRect(const SkMatrix& mx, SkRect* dst, const SkRect& src) {
     *         if (mx.isScaleTranslate()) {
     *             // A scale-translate matrix with a 0 scale factor is not invertible.
     *             if (mx.getScaleX() == 0.f || mx.getScaleY() == 0.f) {
     *                 return false;
     *             }
     *
     *             const SkScalar tx = mx.getTranslateX();
     *             const SkScalar ty = mx.getTranslateY();
     *             // mx maps coordinates as ((sx*x + tx), (sy*y + ty)) so the inverse is
     *             // ((x - tx)/sx), (y - ty)/sy). If sx or sy are negative, we have to swap the edge
     *             // values to maintain a sorted rect.
     *             auto inverted = skvx::float4::Load(&src.fLeft);
     *             inverted -= skvx::float4(tx, ty, tx, ty);
     *
     *             if (mx.getType() > SkMatrix::kTranslate_Mask) {
     *                 const SkScalar sx = 1.f / mx.getScaleX();
     *                 const SkScalar sy = 1.f / mx.getScaleY();
     *                 inverted *= skvx::float4(sx, sy, sx, sy);
     *                 if (sx < 0.f && sy < 0.f) {
     *                     inverted = skvx::shuffle<2, 3, 0, 1>(inverted); // swap L|R and T|B
     *                 } else if (sx < 0.f) {
     *                     inverted = skvx::shuffle<2, 1, 0, 3>(inverted); // swap L|R
     *                 } else if (sy < 0.f) {
     *                     inverted = skvx::shuffle<0, 3, 2, 1>(inverted); // swap T|B
     *                 }
     *             }
     *             inverted.store(&dst->fLeft);
     *             return true;
     *         }
     *
     *         // general case
     *         if (auto inverse = mx.invert()) {
     *             inverse->mapRect(dst, src);
     *             return true;
     *         }
     *         return false;
     *     }
     * ```
     */
    public fun inverseMapRect(
      mx: SkMatrix,
      dst: SkRect?,
      src: SkRect,
    ): Boolean {
      TODO("Implement inverseMapRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static void MapPointsWithStride(const SkMatrix& mx, SkPoint pts[], size_t stride, int count) {
     *         SkASSERT(stride >= sizeof(SkPoint));
     *         SkASSERT(0 == stride % sizeof(SkScalar));
     *
     *         SkMatrix::TypeMask tm = mx.getType();
     *
     *         if (SkMatrix::kIdentity_Mask == tm) {
     *             return;
     *         }
     *         if (SkMatrix::kTranslate_Mask == tm) {
     *             const SkScalar tx = mx.getTranslateX();
     *             const SkScalar ty = mx.getTranslateY();
     *             skvx::float2 trans(tx, ty);
     *             for (int i = 0; i < count; ++i) {
     *                 (skvx::float2::Load(&pts->fX) + trans).store(&pts->fX);
     *                 pts = (SkPoint*)((intptr_t)pts + stride);
     *             }
     *             return;
     *         }
     *         // Insert other special-cases here (e.g. scale+translate)
     *
     *         // general case
     *         if (mx.hasPerspective()) {
     *             for (int i = 0; i < count; ++i) {
     *                 *pts = mx.mapPointPerspective(*pts);
     *                 pts = (SkPoint*)((intptr_t)pts + stride);
     *             }
     *         } else {
     *             for (int i = 0; i < count; ++i) {
     *                 *pts = mx.mapPointAffine(*pts);
     *                 pts = (SkPoint*)((intptr_t)pts + stride);
     *             }
     *         }
     *     }
     * ```
     */
    public fun mapPointsWithStride(
      mx: SkMatrix,
      pts: Array<SkPoint>,
      stride: ULong,
      count: Int,
    ) {
      TODO("Implement mapPointsWithStride")
    }

    /**
     * C++ original:
     * ```cpp
     * static void MapPointsWithStride(const SkMatrix& mx, SkPoint dst[], size_t dstStride,
     *                                     const SkPoint src[], size_t srcStride, int count) {
     *         SkASSERT(srcStride >= sizeof(SkPoint));
     *         SkASSERT(dstStride >= sizeof(SkPoint));
     *         SkASSERT(0 == srcStride % sizeof(SkScalar));
     *         SkASSERT(0 == dstStride % sizeof(SkScalar));
     *         for (int i = 0; i < count; ++i) {
     *             *dst = mx.mapPoint(*src);
     *             src = (SkPoint*)((intptr_t)src + srcStride);
     *             dst = (SkPoint*)((intptr_t)dst + dstStride);
     *         }
     *     }
     * ```
     */
    public fun mapPointsWithStride(
      mx: SkMatrix,
      dst: Array<SkPoint>,
      dstStride: ULong,
      src: Array<SkPoint>,
      srcStride: ULong,
      count: Int,
    ) {
      TODO("Implement mapPointsWithStride")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMatrixPriv::MapHomogeneousPointsWithStride(const SkMatrix& mx, SkPoint3 dst[],
     *                                                   size_t dstStride, const SkPoint3 src[],
     *                                                   size_t srcStride, int count) {
     *     SkASSERT((dst && src && count > 0) || 0 == count);
     *     // no partial overlap
     *     SkASSERT(src == dst || &dst[count] <= &src[0] || &src[count] <= &dst[0]);
     *
     *     if (count > 0) {
     *         if (mx.isIdentity()) {
     *             if (src != dst) {
     *                 if (srcStride == sizeof(SkPoint3) && dstStride == sizeof(SkPoint3)) {
     *                     memcpy(dst, src, count * sizeof(SkPoint3));
     *                 } else {
     *                     for (int i = 0; i < count; ++i) {
     *                         *dst = *src;
     *                         dst = reinterpret_cast<SkPoint3*>(reinterpret_cast<char*>(dst) + dstStride);
     *                         src = reinterpret_cast<const SkPoint3*>(reinterpret_cast<const char*>(src) +
     *                                                                 srcStride);
     *                     }
     *                 }
     *             }
     *             return;
     *         }
     *         do {
     *             SkScalar sx = src->fX;
     *             SkScalar sy = src->fY;
     *             SkScalar sw = src->fZ;
     *             src = reinterpret_cast<const SkPoint3*>(reinterpret_cast<const char*>(src) + srcStride);
     *             const SkScalar* mat = mx.fMat;
     *             typedef SkMatrix M;
     *             SkScalar x = sdot(sx, mat[M::kMScaleX], sy, mat[M::kMSkewX],  sw, mat[M::kMTransX]);
     *             SkScalar y = sdot(sx, mat[M::kMSkewY],  sy, mat[M::kMScaleY], sw, mat[M::kMTransY]);
     *             SkScalar w = sdot(sx, mat[M::kMPersp0], sy, mat[M::kMPersp1], sw, mat[M::kMPersp2]);
     *
     *             dst->set(x, y, w);
     *             dst = reinterpret_cast<SkPoint3*>(reinterpret_cast<char*>(dst) + dstStride);
     *         } while (--count);
     *     }
     * }
     * ```
     */
    public fun mapHomogeneousPointsWithStride(
      mx: SkMatrix,
      dst: Array<SkPoint3>,
      dstStride: ULong,
      src: Array<SkPoint3>,
      srcStride: ULong,
      count: Int,
    ) {
      TODO("Implement mapHomogeneousPointsWithStride")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool PostIDiv(SkMatrix* matrix, int divx, int divy) {
     *         return matrix->postIDiv(divx, divy);
     *     }
     * ```
     */
    public fun postIDiv(
      matrix: SkMatrix?,
      divx: Int,
      divy: Int,
    ): Boolean {
      TODO("Implement postIDiv")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool CheapEqual(const SkMatrix& a, const SkMatrix& b) {
     *         return &a == &b || 0 == memcmp(a.fMat, b.fMat, sizeof(a.fMat));
     *     }
     * ```
     */
    public fun cheapEqual(a: SkMatrix, b: SkMatrix): Boolean {
      TODO("Implement cheapEqual")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkScalar* M44ColMajor(const SkM44& m) { return m.fMat; }
     * ```
     */
    public fun m44ColMajor(m: SkM44): SkScalar {
      TODO("Implement m44ColMajor")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsScaleTranslateAsM33(const SkM44& m) {
     *         return m.rc(1,0) == 0 && m.rc(3,0) == 0 &&
     *                m.rc(0,1) == 0 && m.rc(3,1) == 0 &&
     *                m.rc(3,3) == 1;
     *
     *     }
     * ```
     */
    public fun isScaleTranslateAsM33(m: SkM44): Boolean {
      TODO("Implement isScaleTranslateAsM33")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRect SkMatrixPriv::MapRect(const SkM44& m, const SkRect& src) {
     *     const bool hasPerspective =
     *             m.fMat[3] != 0 || m.fMat[7] != 0 || m.fMat[11] != 0 || m.fMat[15] != 1;
     *     if (hasPerspective) {
     *         return map_rect_perspective(src, m.fMat);
     *     } else {
     *         return map_rect_affine(src, m.fMat);
     *     }
     * }
     * ```
     */
    public fun mapRect(m: SkM44, r: SkRect): SkRect {
      TODO("Implement mapRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkMatrixPriv::DifferentialAreaScale(const SkMatrix& m, const SkPoint& p) {
     *     //              [m00 m01 m02]                                 [f(u,v)]
     *     // Assuming M = [m10 m11 m12], define the projected p'(u,v) = [g(u,v)] where
     *     //              [m20 m12 m22]
     *     //                                                        [x]     [u]
     *     // f(u,v) = x(u,v) / w(u,v), g(u,v) = y(u,v) / w(u,v) and [y] = M*[v]
     *     //                                                        [w]     [1]
     *     //
     *     // Then the differential scale factor between p = (u,v) and p' is |det J|,
     *     // where J is the Jacobian for p': [df/du dg/du]
     *     //                                 [df/dv dg/dv]
     *     // and df/du = (w*dx/du - x*dw/du)/w^2,   dg/du = (w*dy/du - y*dw/du)/w^2
     *     //     df/dv = (w*dx/dv - x*dw/dv)/w^2,   dg/dv = (w*dy/dv - y*dw/dv)/w^2
     *     //
     *     // From here, |det J| can be rewritten as |det J'/w^3|, where
     *     //      [x     y     w    ]   [x   y   w  ]
     *     // J' = [dx/du dy/du dw/du] = [m00 m10 m20]
     *     //      [dx/dv dy/dv dw/dv]   [m01 m11 m21]
     *     SkPoint3 xyw = m.mapPointToHomogeneous(p);
     *
     *     if (xyw.fZ < SK_ScalarNearlyZero) {
     *         // Reaching the discontinuity of xy/w and where the point would clip to w >= 0
     *         return SK_ScalarInfinity;
     *     }
     *     SkMatrix jacobian = SkMatrix::MakeAll(xyw.fX, xyw.fY, xyw.fZ,
     *                                           m.getScaleX(), m.getSkewY(), m.getPerspX(),
     *                                           m.getSkewX(), m.getScaleY(), m.getPerspY());
     *
     *     double denom = 1.0 / xyw.fZ;   // 1/w
     *     denom = denom * denom * denom; // 1/w^3
     *     return SkScalarAbs(SkDoubleToScalar(sk_determinant(jacobian.fMat, true) * denom));
     * }
     * ```
     */
    public fun differentialAreaScale(m: SkMatrix, p: SkPoint): SkScalar {
      TODO("Implement differentialAreaScale")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkMatrixPriv::NearlyAffine(const SkMatrix& m,
     *                                 const SkRect& bounds,
     *                                 SkScalar tolerance) {
     *     if (!m.hasPerspective()) {
     *         return true;
     *     }
     *
     *     // The idea here is that we are computing the differential area scale at each corner,
     *     // and comparing them with some tolerance value. If they are similar, then we can say
     *     // that the transformation is nearly affine.
     *
     *     // We can map the four points simultaneously.
     *     SkPoint3 xyw[4];
     *     m.mapPointsToHomogeneous(xyw, bounds.toQuad());
     *
     *     // Since the Jacobian is a 3x3 matrix, the determinant is a scalar triple product,
     *     // and the initial cross product is constant across all four points.
     *     SkPoint3 v1{m.getScaleX(), m.getSkewY(), m.getPerspX()};
     *     SkPoint3 v2{m.getSkewX(), m.getScaleY(), m.getPerspY()};
     *     SkPoint3 detCrossProd = v1.cross(v2);
     *
     *     // Start with the calculations at P0.
     *     if (xyw[0].fZ < SK_ScalarNearlyZero) {
     *         // Reaching the discontinuity of xy/w and where the point would clip to w >= 0
     *         return false;
     *     }
     *
     *     // Performing a dot product with the pre-w divide transformed point completes
     *     // the scalar triple product and the determinant calculation.
     *     double det = detCrossProd.dot(xyw[0]);
     *     // From that we can compute the differential area scale at P0.
     *     double denom = 1.0 / xyw[0].fZ;   // 1/w
     *     denom = denom * denom * denom; // 1/w^3
     *     SkScalar a0 = SkScalarAbs(SkDoubleToScalar(det*denom));
     *
     *     // Now we compare P0's scale with that at the other three points
     *     tolerance *= tolerance; // squared tolerance since we're comparing area
     *     for (int i = 1; i < 4; ++i) {
     *         if (xyw[i].fZ < SK_ScalarNearlyZero) {
     *             // Reaching the discontinuity of xy/w and where the point would clip to w >= 0
     *             return false;
     *         }
     *
     *         det = detCrossProd.dot(xyw[i]);  // completing scalar triple product
     *         denom = 1.0 / xyw[i].fZ;   // 1/w
     *         denom = denom * denom * denom; // 1/w^3
     *         SkScalar a = SkScalarAbs(SkDoubleToScalar(det*denom));
     *         if (!SkScalarNearlyEqual(a0, a, tolerance)) {
     *             return false;
     *         }
     *     }
     *
     *     return true;
     * }
     * ```
     */
    public fun nearlyAffine(
      m: SkMatrix,
      bounds: SkRect,
      tolerance: SkScalar = TODO(),
    ): Boolean {
      TODO("Implement nearlyAffine")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkMatrixPriv::ComputeResScaleForStroking(const SkMatrix& matrix) {
     *     // Not sure how to handle perspective differently, so we just don't try (yet)
     *     SkScalar sx = SkPoint::Length(matrix[SkMatrix::kMScaleX], matrix[SkMatrix::kMSkewY]);
     *     SkScalar sy = SkPoint::Length(matrix[SkMatrix::kMSkewX],  matrix[SkMatrix::kMScaleY]);
     *     if (SkIsFinite(sx, sy)) {
     *         SkScalar scale = std::max(sx, sy);
     *         if (scale > 0) {
     *             return scale;
     *         }
     *     }
     *     return 1;
     * }
     * ```
     */
    public fun computeResScaleForStroking(matrix: SkMatrix): SkScalar {
      TODO("Implement computeResScaleForStroking")
    }
  }
}
