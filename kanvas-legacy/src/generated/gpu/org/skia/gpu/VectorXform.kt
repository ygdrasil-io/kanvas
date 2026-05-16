package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class VectorXform {
 * public:
 *     AI VectorXform() : fC0{1.0f, 0.f}, fC1{0.f, 1.f} {}
 *     AI explicit VectorXform(const SkMatrix& m) { *this = m; }
 *     AI explicit VectorXform(const SkM44& m) { *this = m; }
 *
 *     AI VectorXform& operator=(const SkMatrix& m) {
 *         SkASSERT(!m.hasPerspective());
 *         fC0 = {m.rc(0,0), m.rc(1,0)};
 *         fC1 = {m.rc(0,1), m.rc(1,1)};
 *         return *this;
 *     }
 *     AI VectorXform& operator=(const SkM44& m) {
 *         SkASSERT(m.rc(3,0) == 0.f && m.rc(3,1) == 0.f && m.rc(3,2) == 0.f && m.rc(3,3) == 1.f);
 *         fC0 = {m.rc(0,0), m.rc(1,0)};
 *         fC1 = {m.rc(0,1), m.rc(1,1)};
 *         return *this;
 *     }
 *     AI skvx::float2 operator()(skvx::float2 vector) const {
 *         return fC0 * vector.x() + fC1 * vector.y();
 *     }
 *     AI skvx::float4 operator()(skvx::float4 vectors) const {
 *         return join(fC0 * vectors.x() + fC1 * vectors.y(),
 *                     fC0 * vectors.z() + fC1 * vectors.w());
 *     }
 * private:
 *     // First and second columns of 2x2 matrix
 *     skvx::float2 fC0;
 *     skvx::float2 fC1;
 * }
 * ```
 */
public data class VectorXform public constructor(
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 fC1
   * ```
   */
  private var fC1: Int,
)
