package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkAlpha
import org.skia.math.SkFixed
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class RunBasedAdditiveBlitter : public AdditiveBlitter {
 * public:
 *     RunBasedAdditiveBlitter(SkBlitter*     realBlitter,
 *                             const SkIRect& ir,
 *                             const SkIRect& clipBounds,
 *                             bool           isInverse);
 *
 *     ~RunBasedAdditiveBlitter() override { this->flush(); }
 *
 *     SkBlitter* getRealBlitter(bool forceRealBlitter) override { return fRealBlitter; }
 *
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], int len) override;
 *     void blitAntiH(int x, int y, SkAlpha alpha) override;
 *     void blitAntiH(int x, int y, int width, SkAlpha alpha) override;
 *
 *     int getWidth() override { return fWidth; }
 *
 *     void flush_if_y_changed(SkFixed y, SkFixed nextY) override {
 *         if (SkFixedFloorToInt(y) != SkFixedFloorToInt(nextY)) {
 *             this->flush();
 *         }
 *     }
 *
 * protected:
 *     SkBlitter* fRealBlitter;
 *
 *     int fCurrY;  // Current y coordinate.
 *     int fWidth;  // Widest row of region to be blitted
 *     int fLeft;   // Leftmost x coordinate in any row
 *     int fTop;    // Initial y coordinate (top of bounds)
 *
 *     // The next three variables are used to track a circular buffer that
 *     // contains the values used in SkAlphaRuns. These variables should only
 *     // ever be updated in advanceRuns(), and fRuns should always point to
 *     // a valid SkAlphaRuns...
 *     int         fRunsToBuffer;
 *     void*       fRunsBuffer;
 *     int         fCurrentRun;
 *     SkAlphaRuns fRuns;
 *
 *     int fOffsetX;
 *
 *     bool check(int x, int width) const { return x >= 0 && x + width <= fWidth; }
 *
 *     // extra one to store the zero at the end
 *     int getRunsSz() const { return (fWidth + 1 + (fWidth + 2) / 2) * sizeof(int16_t); }
 *
 *     // This function updates the fRuns variable to point to the next buffer space
 *     // with adequate storage for a SkAlphaRuns. It mostly just advances fCurrentRun
 *     // and resets fRuns to point to an empty scanline.
 *     void advanceRuns() {
 *         const size_t kRunsSz = this->getRunsSz();
 *         fCurrentRun          = (fCurrentRun + 1) % fRunsToBuffer;
 *         fRuns.fRuns          = reinterpret_cast<int16_t*>(reinterpret_cast<uint8_t*>(fRunsBuffer) +
 *                                                  fCurrentRun * kRunsSz);
 *         fRuns.fAlpha         = reinterpret_cast<SkAlpha*>(fRuns.fRuns + fWidth + 1);
 *         fRuns.reset(fWidth);
 *     }
 *
 *     // Blitting 0xFF and 0 is much faster so we snap alphas close to them
 *     SkAlpha snapAlpha(SkAlpha alpha) { return alpha > 247 ? 0xFF : alpha < 8 ? 0x00 : alpha; }
 *
 *     void flush() {
 *         if (fCurrY >= fTop) {
 *             SkASSERT(fCurrentRun < fRunsToBuffer);
 *             for (int x = 0; fRuns.fRuns[x]; x += fRuns.fRuns[x]) {
 *                 // It seems that blitting 255 or 0 is much faster than blitting 254 or 1
 *                 fRuns.fAlpha[x] = snapAlpha(fRuns.fAlpha[x]);
 *             }
 *             if (!fRuns.empty()) {
 *                 // SkDEBUGCODE(fRuns.dump();)
 *                 fRealBlitter->blitAntiH(fLeft, fCurrY, fRuns.fAlpha, fRuns.fRuns);
 *                 this->advanceRuns();
 *                 fOffsetX = 0;
 *             }
 *             fCurrY = fTop - 1;
 *         }
 *     }
 *
 *     void checkY(int y) {
 *         if (y != fCurrY) {
 *             this->flush();
 *             fCurrY = y;
 *         }
 *     }
 * }
 * ```
 */
public open class RunBasedAdditiveBlitter public constructor(
  realBlitter: SkBlitter?,
  ir: SkIRect,
  clipBounds: SkIRect,
  isInverse: Boolean,
) : AdditiveBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter* fRealBlitter
   * ```
   */
  protected var fRealBlitter: SkBlitter? = TODO("Initialize fRealBlitter")

  /**
   * C++ original:
   * ```cpp
   * int fCurrY
   * ```
   */
  protected var fCurrY: Int = TODO("Initialize fCurrY")

  /**
   * C++ original:
   * ```cpp
   * int fWidth
   * ```
   */
  protected var fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * int fLeft
   * ```
   */
  protected var fLeft: Int = TODO("Initialize fLeft")

  /**
   * C++ original:
   * ```cpp
   * int fTop
   * ```
   */
  protected var fTop: Int = TODO("Initialize fTop")

  /**
   * C++ original:
   * ```cpp
   * int         fRunsToBuffer
   * ```
   */
  protected var fRunsToBuffer: Int = TODO("Initialize fRunsToBuffer")

  /**
   * C++ original:
   * ```cpp
   * void*       fRunsBuffer
   * ```
   */
  protected var fRunsBuffer: Unit? = TODO("Initialize fRunsBuffer")

  /**
   * C++ original:
   * ```cpp
   * int         fCurrentRun
   * ```
   */
  protected var fCurrentRun: Int = TODO("Initialize fCurrentRun")

  /**
   * C++ original:
   * ```cpp
   * SkAlphaRuns fRuns
   * ```
   */
  protected var fRuns: SkAlphaRuns = TODO("Initialize fRuns")

  /**
   * C++ original:
   * ```cpp
   * int fOffsetX
   * ```
   */
  protected var fOffsetX: Int = TODO("Initialize fOffsetX")

  /**
   * C++ original:
   * ```cpp
   * SkBlitter* getRealBlitter(bool forceRealBlitter) override { return fRealBlitter; }
   * ```
   */
  public override fun getRealBlitter(forceRealBlitter: Boolean): SkBlitter {
    TODO("Implement getRealBlitter")
  }

  /**
   * C++ original:
   * ```cpp
   * void RunBasedAdditiveBlitter::blitAntiH(int x, int y, const SkAlpha antialias[], int len) {
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
   *         add_alpha(&fRuns.fAlpha[x + i], antialias[i]);
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
   * void RunBasedAdditiveBlitter::blitAntiH(int x, int y, SkAlpha alpha) {
   *     checkY(y);
   *     x -= fLeft;
   *
   *     if (x < fOffsetX) {
   *         fOffsetX = 0;
   *     }
   *
   *     if (this->check(x, 1)) {
   *         fOffsetX = fRuns.add(x, 0, 1, 0, alpha, fOffsetX);
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
   * void RunBasedAdditiveBlitter::blitAntiH(int x, int y, int width, SkAlpha alpha) {
   *     checkY(y);
   *     x -= fLeft;
   *
   *     if (x < fOffsetX) {
   *         fOffsetX = 0;
   *     }
   *
   *     if (this->check(x, width)) {
   *         fOffsetX = fRuns.add(x, 0, width, 0, alpha, fOffsetX);
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

  /**
   * C++ original:
   * ```cpp
   * int getWidth() override { return fWidth; }
   * ```
   */
  public override fun getWidth(): Int {
    TODO("Implement getWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * void flush_if_y_changed(SkFixed y, SkFixed nextY) override {
   *         if (SkFixedFloorToInt(y) != SkFixedFloorToInt(nextY)) {
   *             this->flush();
   *         }
   *     }
   * ```
   */
  public override fun flushIfYChanged(y: SkFixed, nextY: SkFixed) {
    TODO("Implement flushIfYChanged")
  }

  /**
   * C++ original:
   * ```cpp
   * bool check(int x, int width) const { return x >= 0 && x + width <= fWidth; }
   * ```
   */
  protected fun check(x: Int, width: Int): Boolean {
    TODO("Implement check")
  }

  /**
   * C++ original:
   * ```cpp
   * int getRunsSz() const { return (fWidth + 1 + (fWidth + 2) / 2) * sizeof(int16_t); }
   * ```
   */
  protected fun getRunsSz(): Int {
    TODO("Implement getRunsSz")
  }

  /**
   * C++ original:
   * ```cpp
   * void advanceRuns() {
   *         const size_t kRunsSz = this->getRunsSz();
   *         fCurrentRun          = (fCurrentRun + 1) % fRunsToBuffer;
   *         fRuns.fRuns          = reinterpret_cast<int16_t*>(reinterpret_cast<uint8_t*>(fRunsBuffer) +
   *                                                  fCurrentRun * kRunsSz);
   *         fRuns.fAlpha         = reinterpret_cast<SkAlpha*>(fRuns.fRuns + fWidth + 1);
   *         fRuns.reset(fWidth);
   *     }
   * ```
   */
  protected fun advanceRuns() {
    TODO("Implement advanceRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlpha snapAlpha(SkAlpha alpha) { return alpha > 247 ? 0xFF : alpha < 8 ? 0x00 : alpha; }
   * ```
   */
  protected fun snapAlpha(alpha: SkAlpha): SkAlpha {
    TODO("Implement snapAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * void flush() {
   *         if (fCurrY >= fTop) {
   *             SkASSERT(fCurrentRun < fRunsToBuffer);
   *             for (int x = 0; fRuns.fRuns[x]; x += fRuns.fRuns[x]) {
   *                 // It seems that blitting 255 or 0 is much faster than blitting 254 or 1
   *                 fRuns.fAlpha[x] = snapAlpha(fRuns.fAlpha[x]);
   *             }
   *             if (!fRuns.empty()) {
   *                 // SkDEBUGCODE(fRuns.dump();)
   *                 fRealBlitter->blitAntiH(fLeft, fCurrY, fRuns.fAlpha, fRuns.fRuns);
   *                 this->advanceRuns();
   *                 fOffsetX = 0;
   *             }
   *             fCurrY = fTop - 1;
   *         }
   *     }
   * ```
   */
  protected fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * void checkY(int y) {
   *         if (y != fCurrY) {
   *             this->flush();
   *             fCurrY = y;
   *         }
   *     }
   * ```
   */
  protected fun checkY(y: Int) {
    TODO("Implement checkY")
  }
}
