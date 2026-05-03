package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import kotlin.ULong
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkRectClipBlitter final : public SkBlitter {
 * public:
 *     void init(SkBlitter* blitter, const SkIRect& clipRect) {
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
 *                       SkAlpha leftAlpha, SkAlpha rightAlpha) override;
 *     void blitMask(const SkMask&, const SkIRect& clip) override;
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
public class SkRectClipBlitter : SkBlitter() {
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
   * void SkRectClipBlitter::blitH(int left, int y, int width) {
   *     SkASSERT(width > 0);
   *
   *     if (!y_in_rect(y, fClipRect)) {
   *         return;
   *     }
   *
   *     int right = left + width;
   *
   *     if (left < fClipRect.fLeft) {
   *         left = fClipRect.fLeft;
   *     }
   *     if (right > fClipRect.fRight) {
   *         right = fClipRect.fRight;
   *     }
   *
   *     width = right - left;
   *     if (width > 0) {
   *         fBlitter->blitH(left, y, width);
   *     }
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
   * void SkRectClipBlitter::blitAntiH(int left, int y, const SkAlpha const_aa[],
   *                                   const int16_t const_runs[]) {
   *     SkAlpha* aa = const_cast<SkAlpha*>(const_aa);
   *     int16_t* runs = const_cast<int16_t*>(const_runs);
   *
   *     if (!y_in_rect(y, fClipRect) || left >= fClipRect.fRight) {
   *         return;
   *     }
   *
   *     int x0 = left;
   *     int x1 = left + compute_anti_width(runs);
   *
   *     if (x1 <= fClipRect.fLeft) {
   *         return;
   *     }
   *
   *     SkASSERT(x0 < x1);
   *     if (x0 < fClipRect.fLeft) {
   *         int dx = fClipRect.fLeft - x0;
   *         SkAlphaRuns::BreakAt((int16_t*)runs, (uint8_t*)aa, dx);
   *         runs += dx;
   *         aa += dx;
   *         x0 = fClipRect.fLeft;
   *     }
   *
   *     SkASSERT(x0 < x1 && runs[x1 - x0] == 0);
   *     if (x1 > fClipRect.fRight) {
   *         x1 = fClipRect.fRight;
   *         SkAlphaRuns::BreakAt((int16_t*)runs, (uint8_t*)aa, x1 - x0);
   *         ((int16_t*)runs)[x1 - x0] = 0;
   *     }
   *
   *     SkASSERT(x0 < x1 && runs[x1 - x0] == 0);
   *     SkASSERT(compute_anti_width(runs) == x1 - x0);
   *
   *     fBlitter->blitAntiH(x0, y, aa, runs);
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    constAa: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRectClipBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     SkASSERT(height > 0);
   *
   *     if (!x_in_rect(x, fClipRect)) {
   *         return;
   *     }
   *
   *     int y0 = y;
   *     int y1 = y + height;
   *
   *     if (y0 < fClipRect.fTop) {
   *         y0 = fClipRect.fTop;
   *     }
   *     if (y1 > fClipRect.fBottom) {
   *         y1 = fClipRect.fBottom;
   *     }
   *
   *     if (y0 < y1) {
   *         fBlitter->blitV(x, y0, y1 - y0, alpha);
   *     }
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
   * void SkRectClipBlitter::blitRect(int left, int y, int width, int height) {
   *     SkIRect    r;
   *
   *     r.setLTRB(left, y, left + width, y + height);
   *     if (r.intersect(fClipRect)) {
   *         fBlitter->blitRect(r.fLeft, r.fTop, r.width(), r.height());
   *     }
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
   * void SkRectClipBlitter::blitAntiRect(int left, int y, int width, int height,
   *                                      SkAlpha leftAlpha, SkAlpha rightAlpha) {
   *     SkIRect    r;
   *
   *     // The *true* width of the rectangle blitted is width+2:
   *     r.setLTRB(left, y, left + width + 2, y + height);
   *     if (r.intersect(fClipRect)) {
   *         if (r.fLeft != left) {
   *             SkASSERT(r.fLeft > left);
   *             leftAlpha = 255;
   *         }
   *         if (r.fRight != left + width + 2) {
   *             SkASSERT(r.fRight < left + width + 2);
   *             rightAlpha = 255;
   *         }
   *         if (255 == leftAlpha && 255 == rightAlpha) {
   *             fBlitter->blitRect(r.fLeft, r.fTop, r.width(), r.height());
   *         } else if (1 == r.width()) {
   *             if (r.fLeft == left) {
   *                 fBlitter->blitV(r.fLeft, r.fTop, r.height(), leftAlpha);
   *             } else {
   *                 SkASSERT(r.fLeft == left + width + 1);
   *                 fBlitter->blitV(r.fLeft, r.fTop, r.height(), rightAlpha);
   *             }
   *         } else {
   *             fBlitter->blitAntiRect(r.fLeft, r.fTop, r.width() - 2, r.height(),
   *                                    leftAlpha, rightAlpha);
   *         }
   *     }
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
   * void SkRectClipBlitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *
   *     SkIRect    r = clip;
   *
   *     if (r.intersect(fClipRect)) {
   *         fBlitter->blitMask(mask, r);
   *     }
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
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
