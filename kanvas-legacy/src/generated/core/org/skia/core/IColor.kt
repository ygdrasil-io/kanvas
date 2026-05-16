package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct IColor {
 *     int fR, fG, fB;
 * }
 * ```
 */
public data class IColor public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fR
   * ```
   */
  public var fR: Int,
  /**
   * C++ original:
   * ```cpp
   * int fR, fG
   * ```
   */
  public var fG: Int,
  /**
   * C++ original:
   * ```cpp
   * int fR, fG, fB
   * ```
   */
  public var fB: Int,
)
