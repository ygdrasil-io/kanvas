package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SafeRLEAdditiveBlitter : public RunBasedAdditiveBlitter {
 * public:
 *     SafeRLEAdditiveBlitter(SkBlitter*     realBlitter,
 *                            const SkIRect& ir,
 *                            const SkIRect& clipBounds,
 *                            bool           isInverse)
 *             : RunBasedAdditiveBlitter(realBlitter, ir, clipBounds, isInverse) {}
 *
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], int len) override;
 *     void blitAntiH(int x, int y, SkAlpha alpha) override;
 *     void blitAntiH(int x, int y, int width, SkAlpha alpha) override;
 * }
 * ```
 */
public open class SafeRLEAdditiveBlitter public constructor(
  realBlitter: SkBlitter?,
  ir: SkIRect,
  clipBounds: SkIRect,
  isInverse: Boolean,
) : RunBasedAdditiveBlitter(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SafeRLEAdditiveBlitter::blitAntiH(int x, int y, const SkAlpha antialias[], int len) {
   *     checkY(y);
   *     x -= fLeft;
   *
   *     if (x < 0) {
   *         len += x;
   *         antialias -= x;
   *         x = 0;
   *     }
   *     len = std::min(len, fWidth - x);
   *     SkASSERT(check(x, len));
   *
   *     if (x < fOffsetX) {
   *         fOffsetX = 0;
   *     }
   *
   *     fOffsetX = fRuns.add(x, 0, len, 0, 0, fOffsetX);  // Break the run
   *     for (int i = 0; i < len; i += fRuns.fRuns[x + i]) {
   *         for (int j = 1; j < fRuns.fRuns[x + i]; j++) {
   *             fRuns.fRuns[x + i + j]  = 1;
   *             fRuns.fAlpha[x + i + j] = fRuns.fAlpha[x + i];
   *         }
   *         fRuns.fRuns[x + i] = 1;
   *     }
   *     for (int i = 0; i < len; ++i) {
   *         safely_add_alpha(&fRuns.fAlpha[x + i], antialias[i]);
   *     }
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    len: Int,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SafeRLEAdditiveBlitter::blitAntiH(int x, int y, SkAlpha alpha) {
   *     checkY(y);
   *     x -= fLeft;
   *
   *     if (x < fOffsetX) {
   *         fOffsetX = 0;
   *     }
   *
   *     if (check(x, 1)) {
   *         // Break the run
   *         fOffsetX = fRuns.add(x, 0, 1, 0, 0, fOffsetX);
   *         safely_add_alpha(&fRuns.fAlpha[x], alpha);
   *     }
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    alpha: SkAlpha,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SafeRLEAdditiveBlitter::blitAntiH(int x, int y, int width, SkAlpha alpha) {
   *     checkY(y);
   *     x -= fLeft;
   *
   *     if (x < fOffsetX) {
   *         fOffsetX = 0;
   *     }
   *
   *     if (check(x, width)) {
   *         // Break the run
   *         fOffsetX = fRuns.add(x, 0, width, 0, 0, fOffsetX);
   *         for (int i = x; i < x + width; i += fRuns.fRuns[i]) {
   *             safely_add_alpha(&fRuns.fAlpha[i], alpha);
   *         }
   *     }
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    width: Int,
    alpha: SkAlpha,
  ) {
    TODO("Implement blitAntiH")
  }
}
