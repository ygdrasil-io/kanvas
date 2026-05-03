package org.skia.utils

import kotlin.Int
import org.skia.math.SkM44
import org.skia.math.SkScalar
import org.skia.math.SkV3

/**
 * C++ original:
 * ```cpp
 * class SkPatch3D {
 * public:
 *     SkPatch3D();
 *
 *     void    reset();
 *     void    transform(const SkM44&, SkPatch3D* dst = nullptr) const;
 *
 *     // dot a unit vector with the patch's normal
 *     SkScalar dotWith(SkScalar dx, SkScalar dy, SkScalar dz) const;
 *     SkScalar dotWith(const SkV3& v) const {
 *         return this->dotWith(v.x, v.y, v.z);
 *     }
 *
 *     // deprecated, but still here for animator (for now)
 *     void rotate(SkScalar /*x*/, SkScalar /*y*/, SkScalar /*z*/) {}
 *     void rotateDegrees(SkScalar /*x*/, SkScalar /*y*/, SkScalar /*z*/) {}
 *
 * private:
 * public: // make public for SkDraw3D for now
 *     SkV3  fU, fV;
 *     SkV3  fOrigin;
 *
 *     friend class SkCamera3D;
 * }
 * ```
 */
public data class SkPatch3D public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkV3  fU
   * ```
   */
  public var fU: Int,
  /**
   * C++ original:
   * ```cpp
   * SkV3  fU, fV
   * ```
   */
  public var fV: Int,
  /**
   * C++ original:
   * ```cpp
   * SkV3  fOrigin
   * ```
   */
  public var fOrigin: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkPatch3D::reset() {
   *     fOrigin = {0, 0, 0};
   *     fU = {SK_Scalar1, 0, 0};
   *     fV = {0, -SK_Scalar1, 0};
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPatch3D::transform(const SkM44& m, SkPatch3D* dst) const {
   *     if (dst == nullptr) {
   *         dst = const_cast<SkPatch3D*>(this);
   *     }
   *     dst->fU = m * fU;
   *     dst->fV = m * fV;
   *     auto [x,y,z,_] = m.map(fOrigin.x, fOrigin.y, fOrigin.z, 1);
   *     dst->fOrigin = {x, y, z};
   * }
   * ```
   */
  public fun transform(m: SkM44, dst: SkPatch3D? = TODO()) {
    TODO("Implement transform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkPatch3D::dotWith(SkScalar dx, SkScalar dy, SkScalar dz) const {
   *     SkScalar cx = fU.y * fV.z - fU.z * fV.y;
   *     SkScalar cy = fU.z * fV.x - fU.x * fV.y;
   *     SkScalar cz = fU.x * fV.y - fU.y * fV.x;
   *
   *     return cx * dx + cy * dy + cz * dz;
   * }
   * ```
   */
  public fun dotWith(
    dx: SkScalar,
    dy: SkScalar,
    dz: SkScalar,
  ): Int {
    TODO("Implement dotWith")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar dotWith(const SkV3& v) const {
   *         return this->dotWith(v.x, v.y, v.z);
   *     }
   * ```
   */
  public fun dotWith(v: SkV3): Int {
    TODO("Implement dotWith")
  }

  /**
   * C++ original:
   * ```cpp
   * void rotate(SkScalar /*x*/, SkScalar /*y*/, SkScalar /*z*/) {}
   * ```
   */
  public fun rotate(
    param0: Int,
    param1: Int,
    param2: Int,
  ) {
    TODO("Implement rotate")
  }

  /**
   * C++ original:
   * ```cpp
   * void rotateDegrees(SkScalar /*x*/, SkScalar /*y*/, SkScalar /*z*/) {}
   * ```
   */
  public fun rotateDegrees(
    param0: Int,
    param1: Int,
    param2: Int,
  ) {
    TODO("Implement rotateDegrees")
  }
}
