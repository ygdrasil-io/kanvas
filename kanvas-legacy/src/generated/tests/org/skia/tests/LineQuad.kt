package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.core.SkDLine
import org.skia.core.SkDPoint

/**
 * C++ original:
 * ```cpp
 * struct lineQuad {
 *     QuadPts quad;
 *     SkDLine line;
 *     int result;
 *     SkDPoint expected[2];
 * }
 * ```
 */
public data class LineQuad public constructor(
  /**
   * C++ original:
   * ```cpp
   * QuadPts quad
   * ```
   */
  public var quad: QuadPts,
  /**
   * C++ original:
   * ```cpp
   * SkDLine line
   * ```
   */
  public var line: SkDLine,
  /**
   * C++ original:
   * ```cpp
   * int result
   * ```
   */
  public var result: Int,
  /**
   * C++ original:
   * ```cpp
   * SkDPoint expected[2]
   * ```
   */
  public var expected: Array<SkDPoint>,
)
