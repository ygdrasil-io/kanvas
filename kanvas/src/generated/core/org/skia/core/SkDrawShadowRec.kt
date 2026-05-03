package org.skia.core

import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.math.SkPoint3
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkDrawShadowRec {
 *     SkPoint3    fZPlaneParams;
 *     SkPoint3    fLightPos;
 *     SkScalar    fLightRadius;
 *     SkColor     fAmbientColor;
 *     SkColor     fSpotColor;
 *     uint32_t    fFlags;
 * }
 * ```
 */
public data class SkDrawShadowRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint3    fZPlaneParams
   * ```
   */
  public var fZPlaneParams: SkPoint3,
  /**
   * C++ original:
   * ```cpp
   * SkPoint3    fLightPos
   * ```
   */
  public var fLightPos: SkPoint3,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fLightRadius
   * ```
   */
  public var fLightRadius: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkColor     fAmbientColor
   * ```
   */
  public var fAmbientColor: SkColor,
  /**
   * C++ original:
   * ```cpp
   * SkColor     fSpotColor
   * ```
   */
  public var fSpotColor: SkColor,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fFlags
   * ```
   */
  public var fFlags: Int,
)
