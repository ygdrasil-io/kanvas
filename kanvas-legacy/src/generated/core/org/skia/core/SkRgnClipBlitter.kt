package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import kotlin.ULong
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.foundation.SkRegion
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkRgnClipBlitter final : public SkBlitter {
 * public:
 *     void init(SkBlitter* blitter, const SkRegion* clipRgn) {
 *         SkASSERT(clipRgn && !clipRgn->isEmpty());
 *         fBlitter = blitter;
 *         fRgn = clipRgn;
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
 *     SkBlitter*      fBlitter;
 *     const SkRegion* fRgn;
 * }
 * ```
 */
public class SkRgnClipBlitter : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter*      fBlitter
   * ```
   */
  private var fBlitter: SkBlitter? = TODO("Initialize fBlitter")

  /**
   * C++ original:
   * ```cpp
   * const SkRegion* fRgn
   * ```
   */
  private val fRgn: SkRegion? = TODO("Initialize fRgn")

  /**
   * C++ original:
   * ```cpp
   * void init(SkBlitter* blitter, const SkRegion* clipRgn) {
   *         SkASSERT(clipRgn && !clipRgn->isEmpty());
   *         fBlitter = blitter;
   *         fRgn = clipRgn;
   *     }
   * ```
   */
  public fun `init`(blitter: SkBlitter?, clipRgn: SkRegion?) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRgnClipBlitter::blitH(int x, int y, int width) {
   *     SkRegion::Spanerator span(*fRgn, y, x, x + width);
   *     int left, right;
   *
   *     while (span.next(&left, &right)) {
   *         SkASSERT(left < right);
   *         fBlitter->blitH(left, y, right - left);
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
   * void SkRgnClipBlitter::blitAntiH(int x, int y, const SkAlpha const_aa[],
   *                                  const int16_t const_runs[]) {
   *     SkAlpha* aa = const_cast<SkAlpha*>(const_aa);
   *     int16_t* runs = const_cast<int16_t*>(const_runs);
   *
   *     int width = compute_anti_width(runs);
   *     SkRegion::Spanerator span(*fRgn, y, x, x + width);
   *     int left, right;
   *     SkDEBUGCODE(const SkIRect& bounds = fRgn->getBounds();)
   *
   *     int prevRite = x;
   *     while (span.next(&left, &right)) {
   *         SkASSERT(x <= left);
   *         SkASSERT(left < right);
   *         SkASSERT(left >= bounds.fLeft && right <= bounds.fRight);
   *
   *         SkAlphaRuns::Break((int16_t*)runs, (uint8_t*)aa, left - x, right - left);
   *
   *         // now zero before left
   *         if (left > prevRite) {
   *             int index = prevRite - x;
   *             ((uint8_t*)aa)[index] = 0;   // skip runs after right
   *             ((int16_t*)runs)[index] = SkToS16(left - prevRite);
   *         }
   *
   *         prevRite = right;
   *     }
   *
   *     if (prevRite > x) {
   *         ((int16_t*)runs)[prevRite - x] = 0;
   *
   *         if (x < 0) {
   *             int skip = runs[0];
   *             SkASSERT(skip >= -x);
   *             aa += skip;
   *             runs += skip;
   *             x += skip;
   *         }
   *         fBlitter->blitAntiH(x, y, aa, runs);
   *     }
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
   * void SkRgnClipBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     SkIRect    bounds;
   *     bounds.setXYWH(x, y, 1, height);
   *
   *     SkRegion::Cliperator    iter(*fRgn, bounds);
   *
   *     while (!iter.done()) {
   *         const SkIRect& r = iter.rect();
   *         SkASSERT(bounds.contains(r));
   *
   *         fBlitter->blitV(x, r.fTop, r.height(), alpha);
   *         iter.next();
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
   * void SkRgnClipBlitter::blitRect(int x, int y, int width, int height) {
   *     SkIRect    bounds;
   *     bounds.setXYWH(x, y, width, height);
   *
   *     SkRegion::Cliperator    iter(*fRgn, bounds);
   *
   *     while (!iter.done()) {
   *         const SkIRect& r = iter.rect();
   *         SkASSERT(bounds.contains(r));
   *
   *         fBlitter->blitRect(r.fLeft, r.fTop, r.width(), r.height());
   *         iter.next();
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
   * void SkRgnClipBlitter::blitAntiRect(int x, int y, int width, int height,
   *                                     SkAlpha leftAlpha, SkAlpha rightAlpha) {
   *     // The *true* width of the rectangle to blit is width + 2
   *     SkIRect    bounds;
   *     bounds.setXYWH(x, y, width + 2, height);
   *
   *     SkRegion::Cliperator    iter(*fRgn, bounds);
   *
   *     while (!iter.done()) {
   *         const SkIRect& r = iter.rect();
   *         SkASSERT(bounds.contains(r));
   *         SkASSERT(r.fLeft >= x);
   *         SkASSERT(r.fRight <= x + width + 2);
   *
   *         SkAlpha effectiveLeftAlpha = (r.fLeft == x) ? leftAlpha : 255;
   *         SkAlpha effectiveRightAlpha = (r.fRight == x + width + 2) ?
   *                                       rightAlpha : 255;
   *
   *         if (255 == effectiveLeftAlpha && 255 == effectiveRightAlpha) {
   *             fBlitter->blitRect(r.fLeft, r.fTop, r.width(), r.height());
   *         } else if (1 == r.width()) {
   *             if (r.fLeft == x) {
   *                 fBlitter->blitV(r.fLeft, r.fTop, r.height(),
   *                                 effectiveLeftAlpha);
   *             } else {
   *                 SkASSERT(r.fLeft == x + width + 1);
   *                 fBlitter->blitV(r.fLeft, r.fTop, r.height(),
   *                                 effectiveRightAlpha);
   *             }
   *         } else {
   *             fBlitter->blitAntiRect(r.fLeft, r.fTop, r.width() - 2, r.height(),
   *                                    effectiveLeftAlpha, effectiveRightAlpha);
   *         }
   *         iter.next();
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
   * void SkRgnClipBlitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *
   *     SkRegion::Cliperator iter(*fRgn, clip);
   *     const SkIRect&       r = iter.rect();
   *     SkBlitter*           blitter = fBlitter;
   *
   *     while (!iter.done()) {
   *         blitter->blitMask(mask, r);
   *         iter.next();
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
