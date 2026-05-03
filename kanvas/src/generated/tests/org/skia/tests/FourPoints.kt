package org.skia.tests

import kotlin.Array
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct FourPoints {
 *     SkPoint pts[4];
 * }
 * ```
 */
public data class FourPoints public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint pts[4]
   * ```
   */
  public var pts: Array<SkPoint>,
)
