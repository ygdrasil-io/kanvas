package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct BlurTest {
 *     SkPath (*addPath)();
 *     int viewLen;
 *     SkIRect views[9];
 * }
 * ```
 */
public data class BlurTest public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPath (*addPath)()
   * ```
   */
  public var addPath: () -> SkPath,
  /**
   * C++ original:
   * ```cpp
   * int viewLen
   * ```
   */
  public var viewLen: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect views[9]
   * ```
   */
  public var views: Array<SkIRect>,
)
