package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import kotlin.ULong
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.foundation.U8CPU
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkRectClipCheckBlitter final : public SkBlitter {
 * public:
 *     void init(SkBlitter* blitter, const SkIRect& clipRect) {
 *         SkASSERT(blitter);
 *         SkASSERT(!clipRect.isEmpty());
 *         fBlitter = blitter;
 *         fClipRect = clipRect;
 *     }
 *
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha[], const int16_t runs[]) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitAntiRect(int x, int y, int width, int height,
 *                               SkAlpha leftAlpha, SkAlpha rightAlpha) override;
 *     void blitMask(const SkMask&, const SkIRect& clip) override;
 *     void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) override;
 *     void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) override;
 *
 *     int requestRowsPreserved() const override {
 *         return fBlitter->requestRowsPreserved();
 *     }
 *
 *     void* allocBlitMemory(size_t sz) override {
 *         return fBlitter->allocBlitMemory(sz);
 *     }
 *
 * private:
 *     SkBlitter*  fBlitter;
 *     SkIRect     fClipRect;
 * }
 * ```
 */
public class SkRectClipCheckBlitter : SkBlitter() {
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
   * SkIRect     fClipRect
   * ```
   */
  private var fClipRect: SkIRect = TODO("Initialize fClipRect")

  /**
   * C++ original:
   * ```cpp
   * void init(SkBlitter* blitter, const SkIRect& clipRect) {
   *         SkASSERT(blitter);
   *         SkASSERT(!clipRect.isEmpty());
   *         fBlitter = blitter;
   *         fClipRect = clipRect;
   *     }
   * ```
   */
  public fun `init`(blitter: SkBlitter?, clipRect: SkIRect) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRectClipCheckBlitter::blitH(int x, int y, int width) {
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, width, 1)));
   *     fBlitter->blitH(x, y, width);
   * }
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
   * void SkRectClipCheckBlitter::blitAntiH(int x, int y, const SkAlpha aa[], const int16_t runs[]) {
   *     const int16_t* iter = runs;
   *     for (; *iter; iter += *iter)
   *         ;
   *     int width = iter - runs;
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, width, 1)));
   *     fBlitter->blitAntiH(x, y, aa, runs);
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    aa: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRectClipCheckBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, 1, height)));
   *     fBlitter->blitV(x, y, height, alpha);
   * }
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
   * void SkRectClipCheckBlitter::blitRect(int x, int y, int width, int height) {
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, width, height)));
   *     fBlitter->blitRect(x, y, width, height);
   * }
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
   * void SkRectClipCheckBlitter::blitAntiRect(int x, int y, int width, int height,
   *                                      SkAlpha leftAlpha, SkAlpha rightAlpha) {
   *     bool skipLeft = !leftAlpha;
   *     bool skipRight = !rightAlpha;
   *     SkIRect r = SkIRect::MakeXYWH(x + skipLeft, y, width + 2 - skipRight - skipLeft, height);
   *     SkASSERT(r.isEmpty() || fClipRect.contains(r));
   *     fBlitter->blitAntiRect(x, y, width, height, leftAlpha, rightAlpha);
   * }
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
   * void SkRectClipCheckBlitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *     SkASSERT(fClipRect.contains(clip));
   *     fBlitter->blitMask(mask, clip);
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRectClipCheckBlitter::blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, 2, 1)));
   *     fBlitter->blitAntiH2(x, y, a0, a1);
   * }
   * ```
   */
  public override fun blitAntiH2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiH2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRectClipCheckBlitter::blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
   *     SkASSERT(fClipRect.contains(SkIRect::MakeXYWH(x, y, 1, 2)));
   *     fBlitter->blitAntiV2(x, y, a0, a1);
   * }
   * ```
   */
  public override fun blitAntiV2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiV2")
  }

  /**
   * C++ original:
   * ```cpp
   * int requestRowsPreserved() const override {
   *         return fBlitter->requestRowsPreserved();
   *     }
   * ```
   */
  public override fun requestRowsPreserved(): Int {
    TODO("Implement requestRowsPreserved")
  }

  /**
   * C++ original:
   * ```cpp
   * void* allocBlitMemory(size_t sz) override {
   *         return fBlitter->allocBlitMemory(sz);
   *     }
   * ```
   */
  public override fun allocBlitMemory(sz: ULong) {
    TODO("Implement allocBlitMemory")
  }
}
