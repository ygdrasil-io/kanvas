package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkNullBlitter final : public SkBlitter {
 * public:
 *     void blitH(int x, int y, int width) override {}
 *     void blitAntiH(int x, int y, const SkAlpha[], const int16_t runs[]) override {}
 *     void blitV(int x, int y, int height, SkAlpha alpha) override {}
 *     void blitRect(int x, int y, int width, int height) override {}
 *     void blitMask(const SkMask&, const SkIRect& clip) override {}
 * }
 * ```
 */
public class SkNullBlitter : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * void blitH(int x, int y, int width) override {}
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
   * void blitAntiH(int x, int y, const SkAlpha[], const int16_t runs[]) override {}
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    param2: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitV(int x, int y, int height, SkAlpha alpha) override {}
   * ```
   */
  public override fun blitV(
    x: Int,
    y: Int,
    height: Int,
    alpha: SkAlpha,
  ) {
    TODO("Implement blitV")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitRect(int x, int y, int width, int height) override {}
   * ```
   */
  public override fun blitRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    TODO("Implement blitRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitMask(const SkMask&, const SkIRect& clip) override {}
   * ```
   */
  public override fun blitMask(param0: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }
}
