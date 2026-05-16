package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TestSet {
 *     const Curve* tests;
 *     int testCount;
 * }
 * ```
 */
public data class TestSet public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Curve* tests
   * ```
   */
  public val tests: Curve?,
  /**
   * C++ original:
   * ```cpp
   * int testCount
   * ```
   */
  public var testCount: Int,
)
