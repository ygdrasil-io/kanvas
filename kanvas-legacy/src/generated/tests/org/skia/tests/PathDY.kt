package org.skia.tests

import org.skia.foundation.SkPath
import org.skia.math.SkScalar

public typealias MakePathProc = () -> PathDY

/**
 * C++ original:
 * ```cpp
 * struct PathDY {
 *     SkPath path;
 *     SkScalar dy;
 * }
 * ```
 */
public data class PathDY public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPath path
   * ```
   */
  public var path: SkPath,
  /**
   * C++ original:
   * ```cpp
   * SkScalar dy
   * ```
   */
  public var dy: SkScalar,
)
