package org.skia.tests

import org.skia.core.SkDLine

/**
 * C++ original:
 * ```cpp
 * struct oneLineConic {
 *     ConicPts conic;
 *     SkDLine line;
 * }
 * ```
 */
public data class OneLineConic public constructor(
  /**
   * C++ original:
   * ```cpp
   * ConicPts conic
   * ```
   */
  public var conic: ConicPts,
  /**
   * C++ original:
   * ```cpp
   * SkDLine line
   * ```
   */
  public var line: SkDLine,
)
