package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct D {
 *     int fID;
 * }
 * ```
 */
public data class D public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fID
   * ```
   */
  public var fID: Int,
)
