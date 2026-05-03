package org.skia.tests

import org.skia.core.SkDLine

/**
 * C++ original:
 * ```cpp
 * struct oneLineQuad {
 *     QuadPts quad;
 *     SkDLine line;
 * }
 * ```
 */
public data class OneLineQuad public constructor(
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
)
