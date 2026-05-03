package org.skia.modules

import kotlin.Int
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct ShapedLine {
 *     TArray<ShapedRun> runs;
 *     SkVector fAdvance = { 0, 0 };
 * }
 * ```
 */
public data class ShapedLine public constructor(
  /**
   * C++ original:
   * ```cpp
   * TArray<ShapedRun> runs
   * ```
   */
  public var runs: Int,
  /**
   * C++ original:
   * ```cpp
   * SkVector fAdvance = { 0, 0 }
   * ```
   */
  public var fAdvance: SkVector,
)
