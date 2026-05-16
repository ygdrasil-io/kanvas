package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import kotlin.UByte
import org.skia.foundation.SkAlpha
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * class SkAlphaRuns {
 * public:
 *     int16_t*    fRuns;
 *     uint8_t*     fAlpha;
 *
 *     // Return 0-255 given 0-256
 *     static inline SkAlpha CatchOverflow(int alpha) {
 *         SkASSERT(alpha >= 0 && alpha <= 256);
 *         return alpha - (alpha >> 8);
 *     }
 *
 *     /// Returns true if the scanline contains only a single run,
 *     /// of alpha value 0.
 *     bool empty() const {
 *         SkASSERT(fRuns[0] > 0);
 *         return fAlpha[0] == 0 && fRuns[fRuns[0]] == 0;
 *     }
 *
 *     /// Reinitialize for a new scanline.
 *     void    reset(int width);
 *
 *     /**
 *      *  Insert into the buffer a run starting at (x-offsetX):
 *      *      if startAlpha > 0
 *      *          one pixel with value += startAlpha,
 *      *              max 255
 *      *      if middleCount > 0
 *      *          middleCount pixels with value += maxValue
 *      *      if stopAlpha > 0
 *      *          one pixel with value += stopAlpha
 *      *  Returns the offsetX value that should be passed on the next call,
 *      *  assuming we're on the same scanline. If the caller is switching
 *      *  scanlines, then offsetX should be 0 when this is called.
 *      */
 *     SK_ALWAYS_INLINE int add(int x, U8CPU startAlpha, int middleCount, U8CPU stopAlpha,
 *                              U8CPU maxValue, int offsetX) {
 *         SkASSERT(middleCount >= 0);
 *         SkASSERT(x >= 0 && x + (startAlpha != 0) + middleCount + (stopAlpha != 0) <= fWidth);
 *
 *         SkASSERT(fRuns[offsetX] >= 0);
 *
 *         int16_t*    runs = fRuns + offsetX;
 *         uint8_t*    alpha = fAlpha + offsetX;
 *         uint8_t*    lastAlpha = alpha;
 *         x -= offsetX;
 *
 *         if (startAlpha) {
 *             SkAlphaRuns::Break(runs, alpha, x, 1);
 *             /*  I should be able to just add alpha[x] + startAlpha.
 *                 However, if the trailing edge of the previous span and the leading
 *                 edge of the current span round to the same super-sampled x value,
 *                 I might overflow to 256 with this add, hence the funny subtract (crud).
 *             */
 *             unsigned tmp = alpha[x] + startAlpha;
 *             SkASSERT(tmp <= 256);
 *             alpha[x] = SkToU8(tmp - (tmp >> 8));    // was (tmp >> 7), but that seems wrong if we're trying to catch 256
 *
 *             runs += x + 1;
 *             alpha += x + 1;
 *             x = 0;
 *             SkDEBUGCODE(this->validate();)
 *         }
 *
 *         if (middleCount) {
 *             SkAlphaRuns::Break(runs, alpha, x, middleCount);
 *             alpha += x;
 *             runs += x;
 *             x = 0;
 *             do {
 *                 alpha[0] = SkToU8(CatchOverflow(alpha[0] + maxValue));
 *                 int n = runs[0];
 *                 SkASSERT(n <= middleCount);
 *                 alpha += n;
 *                 runs += n;
 *                 middleCount -= n;
 *             } while (middleCount > 0);
 *             SkDEBUGCODE(this->validate();)
 *             lastAlpha = alpha;
 *         }
 *
 *         if (stopAlpha) {
 *             SkAlphaRuns::Break(runs, alpha, x, 1);
 *             alpha += x;
 *             alpha[0] = SkToU8(alpha[0] + stopAlpha);
 *             SkDEBUGCODE(this->validate();)
 *             lastAlpha = alpha;
 *         }
 *
 *         return SkToS32(lastAlpha - fAlpha);  // new offsetX
 *     }
 *
 *     SkDEBUGCODE(void assertValid(int y, int maxStep) const;)
 *     SkDEBUGCODE(void dump() const;)
 *
 *     /**
 *      * Break the runs in the buffer at offsets x and x+count, properly
 *      * updating the runs to the right and left.
 *      *   i.e. from the state AAAABBBB, run-length encoded as A4B4,
 *      *   Break(..., 2, 5) would produce AAAABBBB rle as A2A2B3B1.
 *      * Allows add() to sum another run to some of the new sub-runs.
 *      *   i.e. adding ..CCCCC. would produce AADDEEEB, rle as A2D2E3B1.
 *      */
 *     static void Break(int16_t runs[], uint8_t alpha[], int x, int count) {
 *         SkASSERT(count > 0 && x >= 0);
 *
 *         //  SkAlphaRuns::BreakAt(runs, alpha, x);
 *         //  SkAlphaRuns::BreakAt(&runs[x], &alpha[x], count);
 *
 *         int16_t* next_runs = runs + x;
 *         uint8_t*  next_alpha = alpha + x;
 *
 *         while (x > 0) {
 *             int n = runs[0];
 *             SkASSERT(n > 0);
 *
 *             if (x < n) {
 *                 alpha[x] = alpha[0];
 *                 runs[0] = SkToS16(x);
 *                 runs[x] = SkToS16(n - x);
 *                 break;
 *             }
 *             runs += n;
 *             alpha += n;
 *             x -= n;
 *         }
 *
 *         runs = next_runs;
 *         alpha = next_alpha;
 *         x = count;
 *
 *         for (;;) {
 *             int n = runs[0];
 *             SkASSERT(n > 0);
 *
 *             if (x < n) {
 *                 alpha[x] = alpha[0];
 *                 runs[0] = SkToS16(x);
 *                 runs[x] = SkToS16(n - x);
 *                 break;
 *             }
 *             x -= n;
 *             if (x <= 0) {
 *                 break;
 *             }
 *             runs += n;
 *             alpha += n;
 *         }
 *     }
 *
 *     /**
 *      * Cut (at offset x in the buffer) a run into two shorter runs with
 *      * matching alpha values.
 *      * Used by the RectClipBlitter to trim a RLE encoding to match the
 *      * clipping rectangle.
 *      */
 *     static void BreakAt(int16_t runs[], uint8_t alpha[], int x) {
 *         while (x > 0) {
 *             int n = runs[0];
 *             SkASSERT(n > 0);
 *
 *             if (x < n) {
 *                 alpha[x] = alpha[0];
 *                 runs[0] = SkToS16(x);
 *                 runs[x] = SkToS16(n - x);
 *                 break;
 *             }
 *             runs += n;
 *             alpha += n;
 *             x -= n;
 *         }
 *     }
 *
 * private:
 *     SkDEBUGCODE(int fWidth;)
 *     SkDEBUGCODE(void validate() const;)
 * }
 * ```
 */
public data class SkAlphaRuns public constructor(
  /**
   * C++ original:
   * ```cpp
   * int16_t*    fRuns
   * ```
   */
  public var fRuns: Int?,
  /**
   * C++ original:
   * ```cpp
   * uint8_t*     fAlpha
   * ```
   */
  public var fAlpha: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool empty() const {
   *         SkASSERT(fRuns[0] > 0);
   *         return fAlpha[0] == 0 && fRuns[fRuns[0]] == 0;
   *     }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAlphaRuns::reset(int width) {
   *     SkASSERT(width > 0);
   *
   * #ifdef SK_DEBUG
   *     SkOpts::memset16((uint16_t*)fRuns, (uint16_t)(-42), width);
   * #endif
   *     fRuns[0] = SkToS16(width);
   *     fRuns[width] = 0;
   *     fAlpha[0] = 0;
   *
   *     SkDEBUGCODE(fWidth = width;)
   *     SkDEBUGCODE(this->validate();)
   * }
   * ```
   */
  public fun reset(width: Int) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * SK_ALWAYS_INLINE int add(int x, U8CPU startAlpha, int middleCount, U8CPU stopAlpha,
   *                              U8CPU maxValue, int offsetX) {
   *         SkASSERT(middleCount >= 0);
   *         SkASSERT(x >= 0 && x + (startAlpha != 0) + middleCount + (stopAlpha != 0) <= fWidth);
   *
   *         SkASSERT(fRuns[offsetX] >= 0);
   *
   *         int16_t*    runs = fRuns + offsetX;
   *         uint8_t*    alpha = fAlpha + offsetX;
   *         uint8_t*    lastAlpha = alpha;
   *         x -= offsetX;
   *
   *         if (startAlpha) {
   *             SkAlphaRuns::Break(runs, alpha, x, 1);
   *             /*  I should be able to just add alpha[x] + startAlpha.
   *                 However, if the trailing edge of the previous span and the leading
   *                 edge of the current span round to the same super-sampled x value,
   *                 I might overflow to 256 with this add, hence the funny subtract (crud).
   *             */
   *             unsigned tmp = alpha[x] + startAlpha;
   *             SkASSERT(tmp <= 256);
   *             alpha[x] = SkToU8(tmp - (tmp >> 8));    // was (tmp >> 7), but that seems wrong if we're trying to catch 256
   *
   *             runs += x + 1;
   *             alpha += x + 1;
   *             x = 0;
   *             SkDEBUGCODE(this->validate();)
   *         }
   *
   *         if (middleCount) {
   *             SkAlphaRuns::Break(runs, alpha, x, middleCount);
   *             alpha += x;
   *             runs += x;
   *             x = 0;
   *             do {
   *                 alpha[0] = SkToU8(CatchOverflow(alpha[0] + maxValue));
   *                 int n = runs[0];
   *                 SkASSERT(n <= middleCount);
   *                 alpha += n;
   *                 runs += n;
   *                 middleCount -= n;
   *             } while (middleCount > 0);
   *             SkDEBUGCODE(this->validate();)
   *             lastAlpha = alpha;
   *         }
   *
   *         if (stopAlpha) {
   *             SkAlphaRuns::Break(runs, alpha, x, 1);
   *             alpha += x;
   *             alpha[0] = SkToU8(alpha[0] + stopAlpha);
   *             SkDEBUGCODE(this->validate();)
   *             lastAlpha = alpha;
   *         }
   *
   *         return SkToS32(lastAlpha - fAlpha);  // new offsetX
   *     }
   * ```
   */
  public fun add(
    x: Int,
    startAlpha: U8CPU,
    middleCount: Int,
    stopAlpha: U8CPU,
    maxValue: U8CPU,
    offsetX: Int,
  ): Int {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAlphaRuns::assertValid(int y, int maxStep) const {
   *         int max = (y + 1) * maxStep - (y == maxStep - 1);
   *
   *         const int16_t* runs = fRuns;
   *         const uint8_t*   alpha = fAlpha;
   *
   *         while (*runs) {
   *             SkASSERT(*alpha <= max);
   *             alpha += *runs;
   *             runs += *runs;
   *         }
   *     }
   * ```
   */
  public fun assertValid(y: Int, maxStep: Int) {
    TODO("Implement assertValid")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAlphaRuns::dump() const {
   *         const int16_t* runs = fRuns;
   *         const uint8_t* alpha = fAlpha;
   *
   *         SkDebugf("Runs");
   *         while (*runs) {
   *             int n = *runs;
   *
   *             SkDebugf(" %02x", *alpha);
   *             if (n > 1) {
   *                 SkDebugf(",%d", n);
   *             }
   *             alpha += n;
   *             runs += n;
   *         }
   *         SkDebugf("\n");
   *     }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAlphaRuns::validate() const {
   *         SkASSERT(fWidth > 0);
   *
   *         int         count = 0;
   *         const int16_t*  runs = fRuns;
   *
   *         while (*runs) {
   *             SkASSERT(*runs > 0);
   *             count += *runs;
   *             SkASSERT(count <= fWidth);
   *             runs += *runs;
   *         }
   *         SkASSERT(count == fWidth);
   *     }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static inline SkAlpha CatchOverflow(int alpha) {
     *         SkASSERT(alpha >= 0 && alpha <= 256);
     *         return alpha - (alpha >> 8);
     *     }
     * ```
     */
    public fun catchOverflow(alpha: Int): SkAlpha {
      TODO("Implement catchOverflow")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Break(int16_t runs[], uint8_t alpha[], int x, int count) {
     *         SkASSERT(count > 0 && x >= 0);
     *
     *         //  SkAlphaRuns::BreakAt(runs, alpha, x);
     *         //  SkAlphaRuns::BreakAt(&runs[x], &alpha[x], count);
     *
     *         int16_t* next_runs = runs + x;
     *         uint8_t*  next_alpha = alpha + x;
     *
     *         while (x > 0) {
     *             int n = runs[0];
     *             SkASSERT(n > 0);
     *
     *             if (x < n) {
     *                 alpha[x] = alpha[0];
     *                 runs[0] = SkToS16(x);
     *                 runs[x] = SkToS16(n - x);
     *                 break;
     *             }
     *             runs += n;
     *             alpha += n;
     *             x -= n;
     *         }
     *
     *         runs = next_runs;
     *         alpha = next_alpha;
     *         x = count;
     *
     *         for (;;) {
     *             int n = runs[0];
     *             SkASSERT(n > 0);
     *
     *             if (x < n) {
     *                 alpha[x] = alpha[0];
     *                 runs[0] = SkToS16(x);
     *                 runs[x] = SkToS16(n - x);
     *                 break;
     *             }
     *             x -= n;
     *             if (x <= 0) {
     *                 break;
     *             }
     *             runs += n;
     *             alpha += n;
     *         }
     *     }
     * ```
     */
    public fun `break`(
      runs: ShortArray,
      alpha: Array<UByte>,
      x: Int,
      count: Int,
    ) {
      TODO("Implement break")
    }

    /**
     * C++ original:
     * ```cpp
     * static void BreakAt(int16_t runs[], uint8_t alpha[], int x) {
     *         while (x > 0) {
     *             int n = runs[0];
     *             SkASSERT(n > 0);
     *
     *             if (x < n) {
     *                 alpha[x] = alpha[0];
     *                 runs[0] = SkToS16(x);
     *                 runs[x] = SkToS16(n - x);
     *                 break;
     *             }
     *             runs += n;
     *             alpha += n;
     *             x -= n;
     *         }
     *     }
     * ```
     */
    public fun breakAt(
      runs: ShortArray,
      alpha: Array<UByte>,
      x: Int,
    ) {
      TODO("Implement breakAt")
    }
  }
}
