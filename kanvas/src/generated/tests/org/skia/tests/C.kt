package org.skia.tests

import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct C {
 *     C() : fID(-1) { ++gInstCnt; }
 *     C(int id) : fID(id) { ++gInstCnt; }
 *     C(C&& c) : C(c.fID) {}
 *     C(const C& c) : C(c.fID) {}
 *
 *     C& operator=(C&&) = default;
 *     C& operator=(const C&) = default;
 *
 *     ~C() { --gInstCnt; }
 *
 *     int fID;
 *
 *     // Under the hood, SkTBlockList and SkBlockAllocator round up to max_align_t. If 'C' was
 *     // just 4 bytes, that often means the internal blocks can squeeze a few extra instances in. This
 *     // is fine, but makes predicting a little trickier, so make sure C is a bit bigger.
 *     int fPadding[4];
 *
 *     static int gInstCnt;
 * }
 * ```
 */
public open class C<C> public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fID
   * ```
   */
  public var fID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPadding[4]
   * ```
   */
  public var fPadding: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * C() : fID(-1) { ++gInstCnt; }
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * C(int id) : fID(id) { ++gInstCnt; }
   * ```
   */
  public constructor(id: Int) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * C(C&& c) : C(c.fID) {}
   * ```
   */
  public constructor(c: C) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * C& operator=(C&&) = default
   * ```
   */
  public fun assign(param0: C) {
    TODO("Implement assign")
  }

  public companion object {
    public var gInstCnt: Int = TODO("Initialize gInstCnt")
  }
}
