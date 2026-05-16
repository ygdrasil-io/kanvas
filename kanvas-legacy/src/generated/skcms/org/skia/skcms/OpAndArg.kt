package org.skia.skcms

import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct OpAndArg {
 *     Op          op;
 *     const void* arg;
 * }
 * ```
 */
public data class OpAndArg public constructor(
  /**
   * C++ original:
   * ```cpp
   * Op          op
   * ```
   */
  public var op: Op,
  /**
   * C++ original:
   * ```cpp
   * const void* arg
   * ```
   */
  public val arg: Unit?,
)
