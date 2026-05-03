package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class AdditiveBlitter : public SkBlitter {
 * public:
 *     ~AdditiveBlitter() override {}
 *
 *     virtual SkBlitter* getRealBlitter(bool forceRealBlitter = false) = 0;
 *
 *     virtual void blitAntiH(int x, int y, const SkAlpha antialias[], int len) = 0;
 *     virtual void blitAntiH(int x, int y, SkAlpha alpha)                = 0;
 *     virtual void blitAntiH(int x, int y, int width, SkAlpha alpha)     = 0;
 *
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
 *         SkDEBUGFAIL("Please call real blitter's blitAntiH instead.");
 *     }
 *
 *     void blitV(int x, int y, int height, SkAlpha alpha) override {
 *         SkDEBUGFAIL("Please call real blitter's blitV instead.");
 *     }
 *
 *     void blitH(int x, int y, int width) override {
 *         SkDEBUGFAIL("Please call real blitter's blitH instead.");
 *     }
 *
 *     void blitRect(int x, int y, int width, int height) override {
 *         SkDEBUGFAIL("Please call real blitter's blitRect instead.");
 *     }
 *
 *     void blitAntiRect(int x, int y, int width, int height, SkAlpha leftAlpha, SkAlpha rightAlpha)
 *             override {
 *         SkDEBUGFAIL("Please call real blitter's blitAntiRect instead.");
 *     }
 *
 *     virtual int getWidth() = 0;
 *
 *     // Flush the additive alpha cache if floor(y) and floor(nextY) is different
 *     // (i.e., we'll start working on a new pixel row).
 *     virtual void flush_if_y_changed(SkFixed y, SkFixed nextY) = 0;
 * }
 * ```
 */
public abstract class AdditiveBlitter : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * virtual SkBlitter* getRealBlitter(bool forceRealBlitter = false) = 0
   * ```
   */
  public abstract fun getRealBlitter(forceRealBlitter: Boolean = false): SkBlitter

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiH(int x, int y, const SkAlpha antialias[], int len) = 0
   * ```
   */
  public abstract override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    len: Int,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiH(int x, int y, SkAlpha alpha)                = 0
   * ```
   */
  public abstract fun blitAntiH(
    x: Int,
    y: Int,
    alpha: SkAlpha,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiH(int x, int y, int width, SkAlpha alpha)     = 0
   * ```
   */
  public abstract fun blitAntiH(
    x: Int,
    y: Int,
    width: Int,
    alpha: SkAlpha,
  )

  /**
   * C++ original:
   * ```cpp
   * void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
   *         SkDEBUGFAIL("Please call real blitter's blitAntiH instead.");
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

  /**
   * C++ original:
   * ```cpp
   * void blitV(int x, int y, int height, SkAlpha alpha) override {
   *         SkDEBUGFAIL("Please call real blitter's blitV instead.");
   *     }
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
   * void blitH(int x, int y, int width) override {
   *         SkDEBUGFAIL("Please call real blitter's blitH instead.");
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
   * void blitRect(int x, int y, int width, int height) override {
   *         SkDEBUGFAIL("Please call real blitter's blitRect instead.");
   *     }
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
   * void blitAntiRect(int x, int y, int width, int height, SkAlpha leftAlpha, SkAlpha rightAlpha)
   *             override {
   *         SkDEBUGFAIL("Please call real blitter's blitAntiRect instead.");
   *     }
   * ```
   */
  public override fun blitAntiRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    leftAlpha: SkAlpha,
    rightAlpha: SkAlpha,
  ) {
    TODO("Implement blitAntiRect")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int getWidth() = 0
   * ```
   */
  public abstract fun getWidth(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void flush_if_y_changed(SkFixed y, SkFixed nextY) = 0
   * ```
   */
  public abstract fun flushIfYChanged(y: SkFixed, nextY: SkFixed)
}
