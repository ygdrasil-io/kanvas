package org.skia.tests

import kotlin.Int
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * struct LevelSizeScenario {
 *     int fBaseWidth;
 *     int fBaseHeight;
 *     int fLevel;
 *     SkISize fExpectedMipMapLevelSize;
 * }
 * ```
 */
public data class LevelSizeScenario public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fBaseWidth
   * ```
   */
  public var fBaseWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * int fBaseHeight
   * ```
   */
  public var fBaseHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * int fLevel
   * ```
   */
  public var fLevel: Int,
  /**
   * C++ original:
   * ```cpp
   * SkISize fExpectedMipMapLevelSize
   * ```
   */
  public var fExpectedMipMapLevelSize: SkISize,
)
