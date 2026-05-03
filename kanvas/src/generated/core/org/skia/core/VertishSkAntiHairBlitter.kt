package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class Vertish_SkAntiHairBlitter : public SkAntiHairBlitter {
 * public:
 *     SkFixed drawCap(int y, SkFixed fx, SkFixed dx, SkFDot6 coverage) override {
 *         fx += SK_FixedHalf;
 *
 *         int x = SkFixedFloorToInt(fx);
 *         U8CPU a = fixed_to_alpha(fx);
 *         this->getBlitter()->blitAntiH2(x - 1, y,
 *                                        scale_alpha_by_coverage(255 - a, coverage), scale_alpha_by_coverage(a, coverage));
 *
 *         return fx + dx - SK_FixedHalf;
 *     }
 *
 *     SkFixed drawLine(int y, int stopy, SkFixed fx, SkFixed dx) override {
 *         SkASSERT(y < stopy);
 *         fx += SK_FixedHalf;
 *         do {
 *             int x = SkFixedFloorToInt(fx);
 *             U8CPU a = fixed_to_alpha(fx);
 *             this->getBlitter()->blitAntiH2(x - 1, y, 255 - a, a);
 *             fx += dx;
 *         } while (++y < stopy);
 *
 *         return fx - SK_FixedHalf;
 *     }
 * }
 * ```
 */
public open class VertishSkAntiHairBlitter : SkAntiHairBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed drawCap(int y, SkFixed fx, SkFixed dx, SkFDot6 coverage) override {
   *         fx += SK_FixedHalf;
   *
   *         int x = SkFixedFloorToInt(fx);
   *         U8CPU a = fixed_to_alpha(fx);
   *         this->getBlitter()->blitAntiH2(x - 1, y,
   *                                        scale_alpha_by_coverage(255 - a, coverage), scale_alpha_by_coverage(a, coverage));
   *
   *         return fx + dx - SK_FixedHalf;
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
   *         fx += SK_FixedHalf;
   *         do {
   *             int x = SkFixedFloorToInt(fx);
   *             U8CPU a = fixed_to_alpha(fx);
   *             this->getBlitter()->blitAntiH2(x - 1, y, 255 - a, a);
   *             fx += dx;
   *         } while (++y < stopy);
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
