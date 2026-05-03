package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class HLine_SkAntiHairBlitter : public SkAntiHairBlitter {
 * public:
 *     SkFixed drawCap(int x, SkFixed fy, SkFixed, SkFDot6 coverage) override {
 *         fy += SK_FixedHalf;
 *
 *         int y = SkFixedFloorToInt(fy);
 *         // Compute an alpha va lue based on the fractional part of fy
 *         // 0 means fy was at NN.5 and we'll only be drawing the upper line.
 *         // 128 means fy was at NN.0 and we'll be coloring both lines approximately
 *         // the same opacity.
 *         U8CPU a = fixed_to_alpha(fy);
 *
 *         // lower line
 *         U8CPU ma = scale_alpha_by_coverage(a, coverage);
 *         if (ma) {
 *             call_hline_blitter(this->getBlitter(), x, y, 1, ma);
 *         }
 *
 *         // upper line
 *         ma = scale_alpha_by_coverage(255 - a, coverage);
 *         if (ma) {
 *             call_hline_blitter(this->getBlitter(), x, y - 1, 1, ma);
 *         }
 *
 *         return fy - SK_FixedHalf;
 *     }
 *
 *     SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed) override {
 *         SkASSERT(x < stopx);
 *         int count = stopx - x;
 *         fy += SK_FixedHalf;
 *
 *         int y = SkFixedFloorToInt(fy);
 *         U8CPU a = fixed_to_alpha(fy);
 *
 *         // lower line
 *         if (a) {
 *             call_hline_blitter(this->getBlitter(), x, y, count, a);
 *         }
 *
 *         // upper line
 *         a = 255 - a;
 *         if (a) {
 *             call_hline_blitter(this->getBlitter(), x, y - 1, count, a);
 *         }
 *
 *         return fy - SK_FixedHalf;
 *     }
 * }
 * ```
 */
public open class HLineSkAntiHairBlitter : SkAntiHairBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed drawCap(int x, SkFixed fy, SkFixed, SkFDot6 coverage) override {
   *         fy += SK_FixedHalf;
   *
   *         int y = SkFixedFloorToInt(fy);
   *         // Compute an alpha va lue based on the fractional part of fy
   *         // 0 means fy was at NN.5 and we'll only be drawing the upper line.
   *         // 128 means fy was at NN.0 and we'll be coloring both lines approximately
   *         // the same opacity.
   *         U8CPU a = fixed_to_alpha(fy);
   *
   *         // lower line
   *         U8CPU ma = scale_alpha_by_coverage(a, coverage);
   *         if (ma) {
   *             call_hline_blitter(this->getBlitter(), x, y, 1, ma);
   *         }
   *
   *         // upper line
   *         ma = scale_alpha_by_coverage(255 - a, coverage);
   *         if (ma) {
   *             call_hline_blitter(this->getBlitter(), x, y - 1, 1, ma);
   *         }
   *
   *         return fy - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawCap(
    x: Int,
    fy: SkFixed,
    param2: SkFixed,
    coverage: SkFDot6,
  ): SkFixed {
    TODO("Implement drawCap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed) override {
   *         SkASSERT(x < stopx);
   *         int count = stopx - x;
   *         fy += SK_FixedHalf;
   *
   *         int y = SkFixedFloorToInt(fy);
   *         U8CPU a = fixed_to_alpha(fy);
   *
   *         // lower line
   *         if (a) {
   *             call_hline_blitter(this->getBlitter(), x, y, count, a);
   *         }
   *
   *         // upper line
   *         a = 255 - a;
   *         if (a) {
   *             call_hline_blitter(this->getBlitter(), x, y - 1, count, a);
   *         }
   *
   *         return fy - SK_FixedHalf;
   *     }
   * ```
   */
  public override fun drawLine(
    x: Int,
    stopx: Int,
    fy: SkFixed,
    param3: SkFixed,
  ): SkFixed {
    TODO("Implement drawLine")
  }
}
