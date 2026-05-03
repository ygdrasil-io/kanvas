package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class RefClass : public SkRefCnt {
 * public:
 *     RefClass(int n) : fN(n) {}
 *     int get() const { return fN; }
 *
 * private:
 *     int fN;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class RefClass public constructor(
  n: Int,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int fN
   * ```
   */
  private var fN: Int = TODO("Initialize fN")

  /**
   * C++ original:
   * ```cpp
   * int get() const { return fN; }
   * ```
   */
  public fun `get`(): Int {
    TODO("Implement get")
  }
}
