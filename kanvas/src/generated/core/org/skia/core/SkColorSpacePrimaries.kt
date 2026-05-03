package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.modules.SkcmsMatrix3x3

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkColorSpacePrimaries {
 *     float fRX;
 *     float fRY;
 *     float fGX;
 *     float fGY;
 *     float fBX;
 *     float fBY;
 *     float fWX;
 *     float fWY;
 *
 *     /**
 *      *  Convert primaries and a white point to a toXYZD50 matrix, the preferred color gamut
 *      *  representation of SkColorSpace.
 *      */
 *     bool toXYZD50(skcms_Matrix3x3* toXYZD50) const;
 * }
 * ```
 */
public data class SkColorSpacePrimaries public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fRX
   * ```
   */
  public var fRX: Float,
  /**
   * C++ original:
   * ```cpp
   * float fRY
   * ```
   */
  public var fRY: Float,
  /**
   * C++ original:
   * ```cpp
   * float fGX
   * ```
   */
  public var fGX: Float,
  /**
   * C++ original:
   * ```cpp
   * float fGY
   * ```
   */
  public var fGY: Float,
  /**
   * C++ original:
   * ```cpp
   * float fBX
   * ```
   */
  public var fBX: Float,
  /**
   * C++ original:
   * ```cpp
   * float fBY
   * ```
   */
  public var fBY: Float,
  /**
   * C++ original:
   * ```cpp
   * float fWX
   * ```
   */
  public var fWX: Float,
  /**
   * C++ original:
   * ```cpp
   * float fWY
   * ```
   */
  public var fWY: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpacePrimaries::toXYZD50(skcms_Matrix3x3* toXYZ_D50) const {
   *     return skcms_PrimariesToXYZD50(fRX, fRY, fGX, fGY, fBX, fBY, fWX, fWY, toXYZ_D50);
   * }
   * ```
   */
  public fun toXYZD50(toXYZD50: SkcmsMatrix3x3?): Boolean {
    TODO("Implement toXYZD50")
  }
}
