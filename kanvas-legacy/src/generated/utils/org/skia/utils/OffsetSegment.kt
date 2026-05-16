package org.skia.utils

import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct OffsetSegment {
 *     SkPoint fP0;
 *     SkVector fV;
 * }
 * ```
 */
public data class OffsetSegment public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fP0
   * ```
   */
  public var fP0: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkVector fV
   * ```
   */
  public var fV: SkVector,
)
