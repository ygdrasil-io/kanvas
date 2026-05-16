package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct LevelCountScenario {
 *     int fWidth;
 *     int fHeight;
 *     int fExpectedLevelCount;
 * }
 * ```
 */
public data class LevelCountScenario public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fWidth
   * ```
   */
  public var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * int fHeight
   * ```
   */
  public var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * int fExpectedLevelCount
   * ```
   */
  public var fExpectedLevelCount: Int,
)
