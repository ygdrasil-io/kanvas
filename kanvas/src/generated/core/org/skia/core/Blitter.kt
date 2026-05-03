package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect
import org.skia.tests.Builder

/**
 * C++ original:
 * ```cpp
 * class SkAAClip::Builder::Blitter final : public SkBlitter {
 *     int fLastY;
 *
 *     /*
 *         If we see a gap of 1 or more empty scanlines while building in Y-order,
 *         we inject an explicit empty scanline (alpha==0)
 *
 *         See AAClipTest.cpp : test_path_with_hole()
 *      */
 *     void checkForYGap(int y) {
 *         SkASSERT(y >= fLastY);
 *         if (fLastY > -SK_MaxS32) {
 *             int gap = y - fLastY;
 *             if (gap > 1) {
 *                 fBuilder->addRun(fLeft, y - 1, 0, fRight - fLeft);
 *             }
 *         }
 *         fLastY = y;
 *     }
 *
 * public:
 *     Blitter(Builder* builder) {
 *         fBuilder = builder;
 *         fLeft = builder->fBounds.fLeft;
 *         fRight = builder->fBounds.fRight;
 *         fMinY = SK_MaxS32;
 *         fLastY = -SK_MaxS32;    // sentinel
 *     }
 *
 *     void finish() {
 *         if (fMinY < SK_MaxS32) {
 *             fBuilder->fMinY = fMinY;
 *         }
 *     }
 *
 *     /**
 *        Must evaluate clips in scan-line order, so don't want to allow blitV(),
 *        but an AAClip can be clipped down to a single pixel wide, so we
 *        must support it (given AntiRect semantics: minimum width is 2).
 *        Instead we'll rely on the runtime asserts to guarantee Y monotonicity;
 *        any failure cases that misses may have minor artifacts.
 *     */
 *     void blitV(int x, int y, int height, SkAlpha alpha) override {
 *         if (height == 1) {
 *             // We're still in scan-line order if height is 1
 *             // This is useful for Analytic AA
 *             const SkAlpha alphas[2] = {alpha, 0};
 *             const int16_t runs[2] = {1, 0};
 *             this->blitAntiH(x, y, alphas, runs);
 *         } else {
 *             this->recordMinY(y);
 *             fBuilder->addColumn(x, y, alpha, height);
 *             fLastY = y + height - 1;
 *         }
 *     }
 *
 *     void blitRect(int x, int y, int width, int height) override {
 *         this->recordMinY(y);
 *         this->checkForYGap(y);
 *         fBuilder->addRectRun(x, y, width, height);
 *         fLastY = y + height - 1;
 *     }
 *
 *     void blitAntiRect(int x, int y, int width, int height,
 *                       SkAlpha leftAlpha, SkAlpha rightAlpha) override {
 *         this->recordMinY(y);
 *         this->checkForYGap(y);
 *         fBuilder->addAntiRectRun(x, y, width, height, leftAlpha, rightAlpha);
 *         fLastY = y + height - 1;
 *     }
 *
 *     void blitMask(const SkMask&, const SkIRect& clip) override
 *         { unexpected(); }
 *
 *     void blitH(int x, int y, int width) override {
 *         this->recordMinY(y);
 *         this->checkForYGap(y);
 *         fBuilder->addRun(x, y, 0xFF, width);
 *     }
 *
 *     void blitAntiH(int x, int y, const SkAlpha alpha[], const int16_t runs[]) override {
 *         this->recordMinY(y);
 *         this->checkForYGap(y);
 *         for (;;) {
 *             int count = *runs;
 *             if (count <= 0) {
 *                 return;
 *             }
 *
 *             // The supersampler's buffer can be the width of the device, so
 *             // we may have to trim the run to our bounds. Previously, we assert that
 *             // the extra spans are always alpha==0.
 *             // However, the analytic AA is too sensitive to precision errors
 *             // so it may have extra spans with very tiny alpha because after several
 *             // arithmatic operations, the edge may bleed the path boundary a little bit.
 *             // Therefore, instead of always asserting alpha==0, we assert alpha < 0x10.
 *             int localX = x;
 *             int localCount = count;
 *             if (x < fLeft) {
 *                 SkASSERT(0x10 > *alpha);
 *                 int gap = fLeft - x;
 *                 SkASSERT(gap <= count);
 *                 localX += gap;
 *                 localCount -= gap;
 *             }
 *             int right = x + count;
 *             if (right > fRight) {
 *                 SkASSERT(0x10 > *alpha);
 *                 localCount -= right - fRight;
 *                 SkASSERT(localCount >= 0);
 *             }
 *
 *             if (localCount) {
 *                 fBuilder->addRun(localX, y, *alpha, localCount);
 *             }
 *             // Next run
 *             runs += count;
 *             alpha += count;
 *             x += count;
 *         }
 *     }
 *
 * private:
 *     Builder* fBuilder;
 *     int      fLeft; // cache of builder's bounds' left edge
 *     int      fRight;
 *     int      fMinY;
 *
 *     /*
 *      *  We track this, in case the scan converter skipped some number of
 *      *  scanlines at the (relative to the bounds it was given). This allows
 *      *  the builder, during its finish, to trip its bounds down to the "real"
 *      *  top.
 *      */
 *     void recordMinY(int y) {
 *         if (y < fMinY) {
 *             fMinY = y;
 *         }
 *     }
 *
 *     void unexpected() {
 *         SK_ABORT("---- did not expect to get called here");
 *     }
 * }
 * ```
 */
public open class Blitter public constructor(
  builder: Builder?,
) : Builder.Blitter(),
    Any,
    SkBlitter {
  /**
   * C++ original:
   * ```cpp
   * int fLastY
   * ```
   */
  private var fLastY: Int = TODO("Initialize fLastY")

  /**
   * C++ original:
   * ```cpp
   * Builder* fBuilder
   * ```
   */
  private var fBuilder: Builder? = TODO("Initialize fBuilder")

  /**
   * C++ original:
   * ```cpp
   * int      fLeft
   * ```
   */
  private var fLeft: Int = TODO("Initialize fLeft")

  /**
   * C++ original:
   * ```cpp
   * int      fRight
   * ```
   */
  private var fRight: Int = TODO("Initialize fRight")

  /**
   * C++ original:
   * ```cpp
   * int      fMinY
   * ```
   */
  private var fMinY: Int = TODO("Initialize fMinY")

  /**
   * C++ original:
   * ```cpp
   * void checkForYGap(int y) {
   *         SkASSERT(y >= fLastY);
   *         if (fLastY > -SK_MaxS32) {
   *             int gap = y - fLastY;
   *             if (gap > 1) {
   *                 fBuilder->addRun(fLeft, y - 1, 0, fRight - fLeft);
   *             }
   *         }
   *         fLastY = y;
   *     }
   * ```
   */
  private fun checkForYGap(y: Int) {
    TODO("Implement checkForYGap")
  }

  /**
   * C++ original:
   * ```cpp
   * void finish() {
   *         if (fMinY < SK_MaxS32) {
   *             fBuilder->fMinY = fMinY;
   *         }
   *     }
   * ```
   */
  public fun finish() {
    TODO("Implement finish")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitV(int x, int y, int height, SkAlpha alpha) override {
   *         if (height == 1) {
   *             // We're still in scan-line order if height is 1
   *             // This is useful for Analytic AA
   *             const SkAlpha alphas[2] = {alpha, 0};
   *             const int16_t runs[2] = {1, 0};
   *             this->blitAntiH(x, y, alphas, runs);
   *         } else {
   *             this->recordMinY(y);
   *             fBuilder->addColumn(x, y, alpha, height);
   *             fLastY = y + height - 1;
   *         }
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
   *         this->recordMinY(y);
   *         this->checkForYGap(y);
   *         fBuilder->addRectRun(x, y, width, height);
   *         fLastY = y + height - 1;
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
   * void blitAntiRect(int x, int y, int width, int height,
   *                       SkAlpha leftAlpha, SkAlpha rightAlpha) override {
   *         this->recordMinY(y);
   *         this->checkForYGap(y);
   *         fBuilder->addAntiRectRun(x, y, width, height, leftAlpha, rightAlpha);
   *         fLastY = y + height - 1;
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
   * void blitMask(const SkMask&, const SkIRect& clip) override
   *         { unexpected(); }
   * ```
   */
  public override fun blitMask(param0: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitH(int x, int y, int width) override {
   *         this->recordMinY(y);
   *         this->checkForYGap(y);
   *         fBuilder->addRun(x, y, 0xFF, width);
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
   * void blitAntiH(int x, int y, const SkAlpha alpha[], const int16_t runs[]) override {
   *         this->recordMinY(y);
   *         this->checkForYGap(y);
   *         for (;;) {
   *             int count = *runs;
   *             if (count <= 0) {
   *                 return;
   *             }
   *
   *             // The supersampler's buffer can be the width of the device, so
   *             // we may have to trim the run to our bounds. Previously, we assert that
   *             // the extra spans are always alpha==0.
   *             // However, the analytic AA is too sensitive to precision errors
   *             // so it may have extra spans with very tiny alpha because after several
   *             // arithmatic operations, the edge may bleed the path boundary a little bit.
   *             // Therefore, instead of always asserting alpha==0, we assert alpha < 0x10.
   *             int localX = x;
   *             int localCount = count;
   *             if (x < fLeft) {
   *                 SkASSERT(0x10 > *alpha);
   *                 int gap = fLeft - x;
   *                 SkASSERT(gap <= count);
   *                 localX += gap;
   *                 localCount -= gap;
   *             }
   *             int right = x + count;
   *             if (right > fRight) {
   *                 SkASSERT(0x10 > *alpha);
   *                 localCount -= right - fRight;
   *                 SkASSERT(localCount >= 0);
   *             }
   *
   *             if (localCount) {
   *                 fBuilder->addRun(localX, y, *alpha, localCount);
   *             }
   *             // Next run
   *             runs += count;
   *             alpha += count;
   *             x += count;
   *         }
   *     }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    alpha: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void recordMinY(int y) {
   *         if (y < fMinY) {
   *             fMinY = y;
   *         }
   *     }
   * ```
   */
  private fun recordMinY(y: Int) {
    TODO("Implement recordMinY")
  }

  /**
   * C++ original:
   * ```cpp
   * void unexpected() {
   *         SK_ABORT("---- did not expect to get called here");
   *     }
   * ```
   */
  private fun unexpected() {
    TODO("Implement unexpected")
  }
}
