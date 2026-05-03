package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * struct SegmentInfo {
 *     SkPath fPath;
 *     int    fPointCount;
 * }
 * ```
 */
public data class SegmentInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  public var fPath: SkPath,
  /**
   * C++ original:
   * ```cpp
   * int    fPointCount
   * ```
   */
  public var fPointCount: Int,
)
