package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct OvalSetOneOff {
 *     int fCol;
 *     int fRow;
 *     int fRot;
 *     int fTrial;
 * }
 * ```
 */
public data class OvalSetOneOff public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCol
   * ```
   */
  public var fCol: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRow
   * ```
   */
  public var fRow: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRot
   * ```
   */
  public var fRot: Int,
  /**
   * C++ original:
   * ```cpp
   * int fTrial
   * ```
   */
  public var fTrial: Int,
)
