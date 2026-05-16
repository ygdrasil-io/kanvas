package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Effect {
 * public:
 *     Effect() : fRefCnt(1) {
 *         gNewCounter += 1;
 *     }
 *     virtual ~Effect() {}
 *
 *     int fRefCnt;
 *
 *     void ref() {
 *         gRefCounter += 1;
 *         fRefCnt += 1;
 *     }
 *     void unref() {
 *         gUnrefCounter += 1;
 *
 *         SkASSERT(fRefCnt > 0);
 *         if (0 == --fRefCnt) {
 *             gDeleteCounter += 1;
 *             delete this;
 *         }
 *     }
 *
 *     int* method() const { return new int; }
 * }
 * ```
 */
public open class Effect public constructor() {
  /**
   * C++ original:
   * ```cpp
   * int fRefCnt
   * ```
   */
  public var fRefCnt: Int = TODO("Initialize fRefCnt")

  /**
   * C++ original:
   * ```cpp
   * void ref() {
   *         gRefCounter += 1;
   *         fRefCnt += 1;
   *     }
   * ```
   */
  public fun ref() {
    TODO("Implement ref")
  }

  /**
   * C++ original:
   * ```cpp
   * void unref() {
   *         gUnrefCounter += 1;
   *
   *         SkASSERT(fRefCnt > 0);
   *         if (0 == --fRefCnt) {
   *             gDeleteCounter += 1;
   *             delete this;
   *         }
   *     }
   * ```
   */
  public fun unref() {
    TODO("Implement unref")
  }

  /**
   * C++ original:
   * ```cpp
   * int* method() const { return new int; }
   * ```
   */
  public fun method(): Int {
    TODO("Implement method")
  }
}
