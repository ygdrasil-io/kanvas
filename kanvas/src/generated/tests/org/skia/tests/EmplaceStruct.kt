package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct EmplaceStruct {
 *     EmplaceStruct(int v) : fValue(v) {}
 *     int fValue;
 * }
 * ```
 */
public data class EmplaceStruct public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fValue
   * ```
   */
  public var fValue: Int,
)
