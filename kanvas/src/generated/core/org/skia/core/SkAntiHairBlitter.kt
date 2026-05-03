package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class SkAntiHairBlitter {
 * public:
 *     SkAntiHairBlitter() : fBlitter(nullptr) {}
 *     virtual ~SkAntiHairBlitter() {}
 *
 *     SkBlitter* getBlitter() const { return fBlitter; }
 *
 *     void setup(SkBlitter* blitter) {
 *         fBlitter = blitter;
 *     }
 *
 *     virtual SkFixed drawCap(int x, SkFixed fy, SkFixed slope, SkFDot6 coverage) = 0;
 *     virtual SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed slope) = 0;
 *
 * private:
 *     SkBlitter*  fBlitter;
 * }
 * ```
 */
public abstract class SkAntiHairBlitter public constructor() {
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
   * SkBlitter* getBlitter() const { return fBlitter; }
   * ```
   */
  public fun getBlitter(): SkBlitter {
    TODO("Implement getBlitter")
  }

  /**
   * C++ original:
   * ```cpp
   * void setup(SkBlitter* blitter) {
   *         fBlitter = blitter;
   *     }
   * ```
   */
  public fun setup(blitter: SkBlitter?) {
    TODO("Implement setup")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkFixed drawCap(int x, SkFixed fy, SkFixed slope, SkFDot6 coverage) = 0
   * ```
   */
  public abstract fun drawCap(
    x: Int,
    fy: SkFixed,
    slope: SkFixed,
    coverage: SkFDot6,
  ): SkFixed

  /**
   * C++ original:
   * ```cpp
   * virtual SkFixed drawLine(int x, int stopx, SkFixed fy, SkFixed slope) = 0
   * ```
   */
  public abstract fun drawLine(
    x: Int,
    stopx: Int,
    fy: SkFixed,
    slope: SkFixed,
  ): SkFixed
}
