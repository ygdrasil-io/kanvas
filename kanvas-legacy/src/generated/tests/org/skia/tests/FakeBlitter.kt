package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.core.SkBlitter
import org.skia.foundation.SkAlpha

/**
 * C++ original:
 * ```cpp
 * struct FakeBlitter : public SkBlitter {
 *     FakeBlitter()
 *         : m_blitCount(0) { }
 *
 *     void blitH(int x, int y, int width) override {
 *         m_blitCount++;
 *     }
 *
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
 *       SkDEBUGFAIL("blitAntiH not implemented");
 *     }
 *
 *     int m_blitCount;
 * }
 * ```
 */
public open class FakeBlitter public constructor(
  /**
   * C++ original:
   * ```cpp
   * int m_blitCount
   * ```
   */
  public var blitCount: Int,
) : SkBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * FakeBlitter()
   *         : m_blitCount(0) { }
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitH(int x, int y, int width) override {
   *         m_blitCount++;
   *     }
   * ```
   */
  public override fun blitH(
    x: Int,
    y: Int,
    width: Int,
  ) {
    TODO("Implement blitH")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
   *       SkDEBUGFAIL("blitAntiH not implemented");
   *     }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }
}
