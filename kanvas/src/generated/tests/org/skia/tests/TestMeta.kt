package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TestMeta {
 *     int fX1;
 *     int fX2;
 * }
 * ```
 */
public data class TestMeta public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fX1
   * ```
   */
  public var fX1: Int,
  /**
   * C++ original:
   * ```cpp
   * int fX2
   * ```
   */
  public var fX2: Int,
)
