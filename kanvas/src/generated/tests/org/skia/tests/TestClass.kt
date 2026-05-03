package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TestClass {
 * public:
 *     TestClass() = default;
 *     TestClass(const TestClass&) = default;
 *     TestClass& operator=(const TestClass&) = default;
 *     TestClass(int v) : value(v) {}
 *     virtual ~TestClass() {}
 *
 *     bool operator==(const TestClass& c) const { return value == c.value; }
 *     bool operator!=(const TestClass& c) const { return value != c.value; }
 *
 *     int value = 0;
 * }
 * ```
 */
public data class TestClass public constructor(
  /**
   * C++ original:
   * ```cpp
   * int value = 0
   * ```
   */
  public var `value`: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TestClass& operator=(const TestClass&) = default
   * ```
   */
  public fun assign(param0: TestClass) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const TestClass& c) const { return value == c.value; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
