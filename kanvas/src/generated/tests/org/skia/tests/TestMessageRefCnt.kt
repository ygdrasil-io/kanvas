package org.skia.tests

import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * struct TestMessageRefCnt : public SkRefCnt {
 *     TestMessageRefCnt(int i, float f) : x(i), y(f) {}
 *
 *     int x;
 *     float y;
 * }
 * ```
 */
public open class TestMessageRefCnt public constructor(
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
) : SkRefCnt(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestMessageRefCnt(int i, float f) : x(i), y(f) {}
   * ```
   */
  public constructor(i: Int, f: Float) : this() {
    TODO("Implement constructor")
  }
}
