package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class VLine_SkAntiHairBlitter : public SkAntiHairBlitter {
 * public:
 *     SkFixed drawCap(int y, SkFixed fx, SkFixed dx, SkFDot6 coverage) override {
 *         SkASSERT(0 == dx);
 *         fx += SK_FixedHalf;
 *
 *         int x = SkFixedFloorToInt(fx);
 *         U8CPU a = fixed_to_alpha(fx);
 *
 *         U8CPU ma = scale_alpha_by_coverage(a, coverage);
 *         if (ma) {
 *             this->getBlitter()->blitV(x, y, 1, ma);
 *         }
 *         ma = scale_alpha_by_coverage(255 - a, coverage);
 *         if (ma) {
 *             this->getBlitter()->blitV(x - 1, y, 1, ma);
 *         }
 *
 *         return fx - SK_FixedHalf;
 *     }
 *
 *     SkFixed drawLine(int y, int stopy, SkFixed fx, SkFixed dx) override {
 *         SkASSERT(y < stopy);
 *         SkASSERT(0 == dx);
 *         fx += SK_FixedHalf;
 *
 *         int x = SkFixedFloorToInt(fx);
 *         U8CPU a = fixed_to_alpha(fx);
 *
 *         if (a) {
 *             this->getBlitter()->blitV(x, y, stopy - y, a);
 *         }
 *         a = 255 - a;
 *         if (a) {
 *             this->getBlitter()->blitV(x - 1, y, stopy - y, a);
 *         }
 *
 *         return fx - SK_FixedHalf;
 *     }
 * }
 * ```
 */
public open class VLineSkAntiHairBlitter : SkAntiHairBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed drawCap(int y, SkFixed fx, SkFixed dx, SkFDot6 coverage) override {
   *         SkASSERT(0 == dx);
   *         fx += SK_FixedHalf;
   *
   *         int x = SkFixedFloorToInt(fx);
   *         U8CPU a = fixed_to_alpha(fx);
   *
   *         U8CPU ma = scale_alpha_by_coverage(a, coverage);
   *         if (ma) {
   *             this->getBlitter()->blitV(x, y, 1, ma);
   *         }
   *         ma = scale_alpha_by_coverage(255 - a, coverage);
   *         if (ma) {
   *             this->getBlitter()->blitV(x - 1, y, 1, ma);
   *         }
   *
   *         return fx - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawCap(
    y: Int,
    fx: SkFixed,
    dx: SkFixed,
    coverage: SkFDot6,
  ): SkFixed {
    TODO("Implement drawCap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed drawLine(int y, int stopy, SkFixed fx, SkFixed dx) override {
   *         SkASSERT(y < stopy);
   *         SkASSERT(0 == dx);
   *         fx += SK_FixedHalf;
   *
   *         int x = SkFixedFloorToInt(fx);
   *         U8CPU a = fixed_to_alpha(fx);
   *
   *         if (a) {
   *             this->getBlitter()->blitV(x, y, stopy - y, a);
   *         }
   *         a = 255 - a;
   *         if (a) {
   *             this->getBlitter()->blitV(x - 1, y, stopy - y, a);
   *         }
   *
   *         return fx - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawLine(
    y: Int,
    stopy: Int,
    fx: SkFixed,
    dx: SkFixed,
  ): SkFixed {
    TODO("Implement drawLine")
  }
}
