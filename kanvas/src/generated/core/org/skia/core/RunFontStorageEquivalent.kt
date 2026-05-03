package org.skia.core

import kotlin.UInt
import kotlin.Unit
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct RunFontStorageEquivalent {
 *     SkScalar fSize, fScaleX;
 *     void*    fTypeface;
 *     SkScalar fSkewX;
 *     uint32_t fFlags;
 * }
 * ```
 */
public data class RunFontStorageEquivalent public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSize
   * ```
   */
  public var fSize: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSize, fScaleX
   * ```
   */
  public var fScaleX: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * void*    fTypeface
   * ```
   */
  public var fTypeface: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fSkewX
   * ```
   */
  public var fSkewX: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fFlags
   * ```
   */
  public var fFlags: UInt,
)
