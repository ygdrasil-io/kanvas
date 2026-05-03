package org.skia.modules

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct LayerInfo {
 *     SkString    fName;
 *     SkSize      fSize;
 *     const float fInPoint,
 *                 fOutPoint;
 * }
 * ```
 */
public data class LayerInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString    fName
   * ```
   */
  public var fName: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSize      fSize
   * ```
   */
  public var fSize: Int,
  /**
   * C++ original:
   * ```cpp
   * const float fInPoint
   * ```
   */
  public val fInPoint: Float,
  /**
   * C++ original:
   * ```cpp
   * const float fInPoint,
   *                 fOutPoint
   * ```
   */
  public val fOutPoint: Float,
)
