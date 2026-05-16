package org.skia.tests

import kotlin.Char
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Legal {
 *     char fSymbol;
 *     int fScalars;
 * }
 * ```
 */
public data class Legal public constructor(
  /**
   * C++ original:
   * ```cpp
   * char fSymbol
   * ```
   */
  public var fSymbol: Char,
  /**
   * C++ original:
   * ```cpp
   * int fScalars
   * ```
   */
  public var fScalars: Int,
)
