package org.skia.tests

import kotlin.Float
import org.skia.math.SkV3

/**
 * C++ original:
 * ```cpp
 * struct Info {
 *     float   fNear = 0.05f;
 *     float   fFar = 4;
 *     float   fAngle = SK_ScalarPI / 4;
 *
 *     SkV3    fEye { 0, 0, 1.0f/std::tan(fAngle/2) - 1 };
 *     SkV3    fCOA { 0, 0, 0 };
 *     SkV3    fUp  { 0, 1, 0 };
 * }
 * ```
 */
public data class Info public constructor(
  /**
   * C++ original:
   * ```cpp
   * float   fNear = 0.05f
   * ```
   */
  public var fNear: Float,
  /**
   * C++ original:
   * ```cpp
   * float   fFar = 4
   * ```
   */
  public var fFar: Float,
  /**
   * C++ original:
   * ```cpp
   * float   fAngle = SK_ScalarPI / 4
   * ```
   */
  public var fAngle: Float,
  /**
   * C++ original:
   * ```cpp
   * SkV3    fEye
   * ```
   */
  public var fEye: SkV3,
  /**
   * C++ original:
   * ```cpp
   * SkV3    fCOA { 0, 0, 0 }
   * ```
   */
  public var fCOA: SkV3,
  /**
   * C++ original:
   * ```cpp
   * SkV3    fUp  { 0, 1, 0 }
   * ```
   */
  public var fUp: SkV3,
)
