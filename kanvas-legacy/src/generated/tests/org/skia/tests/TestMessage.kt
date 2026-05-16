package org.skia.tests

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TestMessage {
 *     TestMessage(int i, float f) : x(i), y(f) {}
 *
 *     int x;
 *     float y;
 * }
 * ```
 */
public data class TestMessage public constructor(
  /**
   * C++ original:
   * ```cpp
   * int x
   * ```
   */
  public var x: Int,
  /**
   * C++ original:
   * ```cpp
   * float y
   * ```
   */
  public var y: Float,
)
