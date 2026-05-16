package org.skia.core

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct YUVCoeff {
 *     float   Kr, Kb;
 *     int     bits;
 *     Range   range;
 * }
 * ```
 */
public data class YUVCoeff public constructor(
  /**
   * C++ original:
   * ```cpp
   * float   Kr
   * ```
   */
  public var kr: Float,
  /**
   * C++ original:
   * ```cpp
   * float   Kr, Kb
   * ```
   */
  public var kb: Float,
  /**
   * C++ original:
   * ```cpp
   * int     bits
   * ```
   */
  public var bits: Int,
  /**
   * C++ original:
   * ```cpp
   * Range   range
   * ```
   */
  public var range: Range,
)
