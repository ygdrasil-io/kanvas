package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class Horish_SkAntiHairBlitter : public SkAntiHairBlitter {
 * public:
 *     SkFixed drawCap(int x, SkFixed fy, SkFixed dy, SkFDot6 coverage) override {
 *         fy += SK_FixedHalf;
 *
 *         int lower_y = SkFixedFloorToInt(fy);
 *         U8CPU  a = fixed_to_alpha(fy);
 *         U8CPU a0 = scale_alpha_by_coverage(255 - a, coverage);
 *         U8CPU a1 = scale_alpha_by_coverage(a, coverage);
 *         this->getBlitter()->blitAntiV2(x, lower_y - 1, a0, a1);
 *
 *         return fy + dy - SK_FixedHalf;
 *     }
 *
 *     SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed dy) override {
 *         SkASSERT(x < stopx);
 *
 *         fy += SK_FixedHalf;
 *         SkBlitter* blitter = this->getBlitter();
 *         do {
 *             int lower_y = SkFixedFloorToInt(fy);
 *             U8CPU a = fixed_to_alpha(fy);
 *             blitter->blitAntiV2(x, lower_y - 1, 255 - a, a);
 *             fy += dy;
 *         } while (++x < stopx);
 *
 *         return fy - SK_FixedHalf;
 *     }
 * }
 * ```
 */
public open class HorishSkAntiHairBlitter : SkAntiHairBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed drawCap(int x, SkFixed fy, SkFixed dy, SkFDot6 coverage) override {
   *         fy += SK_FixedHalf;
   *
   *         int lower_y = SkFixedFloorToInt(fy);
   *         U8CPU  a = fixed_to_alpha(fy);
   *         U8CPU a0 = scale_alpha_by_coverage(255 - a, coverage);
   *         U8CPU a1 = scale_alpha_by_coverage(a, coverage);
   *         this->getBlitter()->blitAntiV2(x, lower_y - 1, a0, a1);
   *
   *         return fy + dy - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawCap(
    x: Int,
    fy: SkFixed,
    dy: SkFixed,
    coverage: SkFDot6,
  ): SkFixed {
    TODO("Implement drawCap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed dy) override {
   *         SkASSERT(x < stopx);
   *
   *         fy += SK_FixedHalf;
   *         SkBlitter* blitter = this->getBlitter();
   *         do {
   *             int lower_y = SkFixedFloorToInt(fy);
   *             U8CPU a = fixed_to_alpha(fy);
   *             blitter->blitAntiV2(x, lower_y - 1, 255 - a, a);
   *             fy += dy;
   *         } while (++x < stopx);
   *
   *         return fy - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawLine(
    x: Int,
    stopx: Int,
    fy: SkFixed,
    dy: SkFixed,
  ): SkFixed {
    TODO("Implement drawLine")
  }
}
