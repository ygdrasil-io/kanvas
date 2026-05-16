package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DashExample {
 *     int* pattern;
 *     int length;
 * }
 * ```
 */
public data class DashExample public constructor(
  /**
   * C++ original:
   * ```cpp
   * int* pattern
   * ```
   */
  public var pattern: Int?,
  /**
   * C++ original:
   * ```cpp
   * int length
   * ```
   */
  public var length: Int,
)
