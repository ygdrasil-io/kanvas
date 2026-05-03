package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class InverseBlitter : public SkBlitter {
 * public:
 *     void setBlitter(SkBlitter* blitter, const SkIRect& clip) {
 *         fBlitter = blitter;
 *         fFirstX = clip.fLeft;
 *         fLastX = clip.fRight;
 *     }
 *     void prepost(int y, bool isStart) {
 *         if (isStart) {
 *             fPrevX = fFirstX;
 *         } else {
 *             int invWidth = fLastX - fPrevX;
 *             if (invWidth > 0) {
 *                 fBlitter->blitH(fPrevX, y, invWidth);
 *             }
 *         }
 *     }
 *
 *     // overrides
 *     void blitH(int x, int y, int width) override {
 *         int invWidth = x - fPrevX;
 *         if (invWidth > 0) {
 *             fBlitter->blitH(fPrevX, y, invWidth);
 *         }
 *         fPrevX = x + width;
 *     }
 *
 *     // we do not expect to get called with these entrypoints
 *     void blitAntiH(int, int, const SkAlpha[], const int16_t runs[]) override {
 *         SkDEBUGFAIL("blitAntiH unexpected");
 *     }
 *     void blitV(int x, int y, int height, SkAlpha alpha) override {
 *         SkDEBUGFAIL("blitV unexpected");
 *     }
 *     void blitRect(int x, int y, int width, int height) override {
 *         SkDEBUGFAIL("blitRect unexpected");
 *     }
 *     void blitMask(const SkMask&, const SkIRect& clip) override {
 *         SkDEBUGFAIL("blitMask unexpected");
 *     }
 *
 * private:
 *     SkBlitter*  fBlitter;
 *     int         fFirstX, fLastX, fPrevX;
 * }
 * ```
 */
public open class InverseBlitter : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter*  fBlitter
   * ```
   */
  private var fBlitter: SkBlitter? = TODO("Initialize fBlitter")

  /**
   * C++ original:
   * ```cpp
   * int         fFirstX
   * ```
   */
  private var fFirstX: Int = TODO("Initialize fFirstX")

  /**
   * C++ original:
   * ```cpp
   * int         fFirstX, fLastX
   * ```
   */
  private var fLastX: Int = TODO("Initialize fLastX")

  /**
   * C++ original:
   * ```cpp
   * int         fFirstX, fLastX, fPrevX
   * ```
   */
  private var fPrevX: Int = TODO("Initialize fPrevX")

  /**
   * C++ original:
   * ```cpp
   * void setBlitter(SkBlitter* blitter, const SkIRect& clip) {
   *         fBlitter = blitter;
   *         fFirstX = clip.fLeft;
   *         fLastX = clip.fRight;
   *     }
   * ```
   */
  public fun setBlitter(blitter: SkBlitter?, clip: SkIRect) {
    TODO("Implement setBlitter")
  }

  /**
   * C++ original:
   * ```cpp
   * void prepost(int y, bool isStart) {
   *         if (isStart) {
   *             fPrevX = fFirstX;
   *         } else {
   *             int invWidth = fLastX - fPrevX;
   *             if (invWidth > 0) {
   *                 fBlitter->blitH(fPrevX, y, invWidth);
   *             }
   *         }
   *     }
   * ```
   */
  public fun prepost(y: Int, isStart: Boolean) {
    TODO("Implement prepost")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitH(int x, int y, int width) override {
   *         int invWidth = x - fPrevX;
   *         if (invWidth > 0) {
   *             fBlitter->blitH(fPrevX, y, invWidth);
   *         }
   *         fPrevX = x + width;
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
   * void blitAntiH(int, int, const SkAlpha[], const int16_t runs[]) override {
   *         SkDEBUGFAIL("blitAntiH unexpected");
   *     }
   * ```
   */
  public override fun blitAntiH(
    param0: Int,
    param1: Int,
    param2: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitV(int x, int y, int height, SkAlpha alpha) override {
   *         SkDEBUGFAIL("blitV unexpected");
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
   * void blitRect(int x, int y, int width, int height) override {
   *         SkDEBUGFAIL("blitRect unexpected");
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
   * void blitMask(const SkMask&, const SkIRect& clip) override {
   *         SkDEBUGFAIL("blitMask unexpected");
   *     }
   * ```
   */
  public override fun blitMask(param0: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }
}
