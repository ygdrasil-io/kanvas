package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkCamera3D {
 * public:
 *     SkCamera3D();
 *
 *     void reset();
 *     void update();
 *     void patchToMatrix(const SkPatch3D&, SkMatrix* matrix) const;
 *
 *     SkV3   fLocation;   // origin of the camera's space
 *     SkV3   fAxis;       // view direction
 *     SkV3   fZenith;     // up direction
 *     SkV3   fObserver;   // eye position (may not be the same as the origin)
 *
 * private:
 *     mutable SkMatrix    fOrientation;
 *     mutable bool        fNeedToUpdate;
 *
 *     void doUpdate() const;
 * }
 * ```
 */
public data class SkCamera3D public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkV3   fLocation
   * ```
   */
  public var fLocation: Int,
  /**
   * C++ original:
   * ```cpp
   * SkV3   fAxis
   * ```
   */
  public var fAxis: Int,
  /**
   * C++ original:
   * ```cpp
   * SkV3   fZenith
   * ```
   */
  public var fZenith: Int,
  /**
   * C++ original:
   * ```cpp
   * SkV3   fObserver
   * ```
   */
  public var fObserver: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable SkMatrix    fOrientation
   * ```
   */
  private var fOrientation: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable bool        fNeedToUpdate
   * ```
   */
  private var fNeedToUpdate: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkCamera3D::reset() {
   *     fLocation = {0, 0, -SkIntToScalar(576)};   // 8 inches backward
   *     fAxis = {0, 0, SK_Scalar1};                // forward
   *     fZenith = {0, -SK_Scalar1, 0};             // up
   *
   *     fObserver = {0, 0, fLocation.z};
   *
   *     fNeedToUpdate = true;
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCamera3D::update() {
   *     fNeedToUpdate = true;
   * }
   * ```
   */
  public fun update() {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCamera3D::patchToMatrix(const SkPatch3D& quilt, SkMatrix* matrix) const {
   *     if (fNeedToUpdate) {
   *         this->doUpdate();
   *         fNeedToUpdate = false;
   *     }
   *
   *     const SkScalar* mapPtr = (const SkScalar*)(const void*)&fOrientation;
   *     const SkScalar* patchPtr;
   *
   *     SkV3 diff = quilt.fOrigin - fLocation;
   *     SkScalar dot = diff.dot({mapPtr[6], mapPtr[7], mapPtr[8]});
   *
   *     // This multiplies fOrientation by the matrix [quilt.fU quilt.fV diff] -- U, V, and diff are
   *     // column vectors in the matrix -- then divides by the length of the projection of diff onto
   *     // the view axis (which is 'dot'). This transforms the patch (which transforms from local path
   *     // space to world space) into view space (since fOrientation transforms from world space to
   *     // view space).
   *     //
   *     // The divide by 'dot' isn't strictly necessary as the homogeneous divide would do much the
   *     // same thing (it's just scaling the entire matrix by 1/dot). It looks like it's normalizing
   *     // the matrix into some canonical space.
   *     patchPtr = (const SkScalar*)&quilt;
   *     matrix->set(SkMatrix::kMScaleX, SkScalarDotDiv(3, patchPtr, 1, mapPtr, 1, dot));
   *     matrix->set(SkMatrix::kMSkewY,  SkScalarDotDiv(3, patchPtr, 1, mapPtr+3, 1, dot));
   *     matrix->set(SkMatrix::kMPersp0, SkScalarDotDiv(3, patchPtr, 1, mapPtr+6, 1, dot));
   *
   *     patchPtr += 3;
   *     matrix->set(SkMatrix::kMSkewX,  SkScalarDotDiv(3, patchPtr, 1, mapPtr, 1, dot));
   *     matrix->set(SkMatrix::kMScaleY, SkScalarDotDiv(3, patchPtr, 1, mapPtr+3, 1, dot));
   *     matrix->set(SkMatrix::kMPersp1, SkScalarDotDiv(3, patchPtr, 1, mapPtr+6, 1, dot));
   *
   *     patchPtr = (const SkScalar*)(const void*)&diff;
   *     matrix->set(SkMatrix::kMTransX, SkScalarDotDiv(3, patchPtr, 1, mapPtr, 1, dot));
   *     matrix->set(SkMatrix::kMTransY, SkScalarDotDiv(3, patchPtr, 1, mapPtr+3, 1, dot));
   *     matrix->set(SkMatrix::kMPersp2, SK_Scalar1);
   * }
   * ```
   */
  public fun patchToMatrix(quilt: SkPatch3D, matrix: SkMatrix?) {
    TODO("Implement patchToMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCamera3D::doUpdate() const {
   *     SkV3    axis, zenith, cross;
   *
   *     // construct a orthonormal basis of cross (x), zenith (y), and axis (z)
   *     axis = fAxis.normalize();
   *
   *     zenith = fZenith - (axis * fZenith) * axis;
   *     zenith = zenith.normalize();
   *
   *     cross = axis.cross(zenith);
   *
   *     {
   *         SkMatrix* orien = &fOrientation;
   *         auto [x, y, z] = fObserver;
   *
   *         // Looking along the view axis we have:
   *         //
   *         //   /|\ zenith
   *         //    |
   *         //    |
   *         //    |  * observer (projected on XY plane)
   *         //    |
   *         //    |____________\ cross
   *         //                 /
   *         //
   *         // So this does a z-shear along the view axis based on the observer's x and y values,
   *         // and scales in x and y relative to the negative of the observer's z value
   *         // (the observer is in the negative z direction).
   *
   *         orien->set(SkMatrix::kMScaleX, x * axis.x - z * cross.x);
   *         orien->set(SkMatrix::kMSkewX,  x * axis.y - z * cross.y);
   *         orien->set(SkMatrix::kMTransX, x * axis.z - z * cross.z);
   *         orien->set(SkMatrix::kMSkewY,  y * axis.x - z * zenith.x);
   *         orien->set(SkMatrix::kMScaleY, y * axis.y - z * zenith.y);
   *         orien->set(SkMatrix::kMTransY, y * axis.z - z * zenith.z);
   *         orien->set(SkMatrix::kMPersp0, axis.x);
   *         orien->set(SkMatrix::kMPersp1, axis.y);
   *         orien->set(SkMatrix::kMPersp2, axis.z);
   *     }
   * }
   * ```
   */
  private fun doUpdate() {
    TODO("Implement doUpdate")
  }
}
