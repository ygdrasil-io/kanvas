package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.foundation.SkAlpha
import org.skia.math.SkFixed
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class MaskAdditiveBlitter : public AdditiveBlitter {
 * public:
 *     MaskAdditiveBlitter(SkBlitter*     realBlitter,
 *                         const SkIRect& ir,
 *                         const SkIRect& clipBounds,
 *                         bool           isInverse);
 *     ~MaskAdditiveBlitter() override { fRealBlitter->blitMask(fMask, fClipRect); }
 *
 *     // Most of the time, we still consider this mask blitter as the real blitter
 *     // so we can accelerate blitRect and others. But sometimes we want to return
 *     // the absolute real blitter (e.g., when we fall back to the old code path).
 *     SkBlitter* getRealBlitter(bool forceRealBlitter) override {
 *         return forceRealBlitter ? fRealBlitter : this;
 *     }
 *
 *     // Virtual function is slow. So don't use this. Directly add alpha to the mask instead.
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], int len) override;
 *
 *     // Allowing following methods are used to blit rectangles during aaa_walk_convex_edges
 *     // Since there aren't many rectangles, we can still bear the slow speed of virtual functions.
 *     void blitAntiH(int x, int y, SkAlpha alpha) override;
 *     void blitAntiH(int x, int y, int width, SkAlpha alpha) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitAntiRect(int x, int y, int width, int height, SkAlpha leftAlpha, SkAlpha rightAlpha)
 *             override;
 *
 *     // The flush is only needed for RLE (RunBasedAdditiveBlitter)
 *     void flush_if_y_changed(SkFixed y, SkFixed nextY) override {}
 *
 *     int getWidth() override { return fClipRect.width(); }
 *
 *     static bool CanHandleRect(const SkIRect& bounds) {
 *         int width = bounds.width();
 *         if (width > MaskAdditiveBlitter::kMAX_WIDTH) {
 *             return false;
 *         }
 *         int64_t rb = SkAlign4(width);
 *         // use 64bits to detect overflow
 *         int64_t storage = rb * bounds.height();
 *
 *         return (width <= MaskAdditiveBlitter::kMAX_WIDTH) &&
 *                (storage <= MaskAdditiveBlitter::kMAX_STORAGE);
 *     }
 *
 *     // Return a pointer where pointer[x] corresonds to the alpha of (x, y)
 *     uint8_t* getRow(int y) {
 *         if (y != fY) {
 *             fY   = y;
 *             fRow = fMask.image() + (y - fMask.fBounds.fTop) * fMask.fRowBytes - fMask.fBounds.fLeft;
 *         }
 *         return fRow;
 *     }
 *
 * private:
 *     // so we don't try to do very wide things, where the RLE blitter would be faster
 *     static const int kMAX_WIDTH   = 32;
 *     static const int kMAX_STORAGE = 1024;
 *
 *     SkBlitter* fRealBlitter;
 *     SkMaskBuilder fMask;
 *     SkIRect    fClipRect;
 *     // we add 2 because we can write 1 extra byte at either end due to precision error
 *     uint32_t fStorage[(kMAX_STORAGE >> 2) + 2];
 *
 *     uint8_t* fRow;
 *     int      fY;
 * }
 * ```
 */
public open class MaskAdditiveBlitter public constructor(
  realBlitter: SkBlitter?,
  ir: SkIRect,
  clipBounds: SkIRect,
  isInverse: Boolean,
) : AdditiveBlitter() {
  /**
   * C++ original:
   * ```cpp
   * static const int kMAX_WIDTH   = 32
   * ```
   */
  private var fRealBlitter: SkBlitter? = TODO("Initialize fRealBlitter")

  /**
   * C++ original:
   * ```cpp
   * static const int kMAX_STORAGE = 1024
   * ```
   */
  private var fMask: SkMaskBuilder = TODO("Initialize fMask")

  /**
   * C++ original:
   * ```cpp
   * SkBlitter* fRealBlitter
   * ```
   */
  private var fClipRect: SkIRect = TODO("Initialize fClipRect")

  /**
   * C++ original:
   * ```cpp
   * SkMaskBuilder fMask
   * ```
   */
  private var fStorage: IntArray = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * SkIRect    fClipRect
   * ```
   */
  private var fRow: Int? = TODO("Initialize fRow")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fStorage[(kMAX_STORAGE >> 2) + 2]
   * ```
   */
  private var fY: Int = TODO("Initialize fY")

  /**
   * C++ original:
   * ```cpp
   * SkBlitter* getRealBlitter(bool forceRealBlitter) override {
   *         return forceRealBlitter ? fRealBlitter : this;
   *     }
   * ```
   */
  public override fun getRealBlitter(forceRealBlitter: Boolean): SkBlitter {
    TODO("Implement getRealBlitter")
  }

  /**
   * C++ original:
   * ```cpp
   * void MaskAdditiveBlitter::blitAntiH(int x, int y, const SkAlpha antialias[], int len) {
   *     SK_ABORT("Don't use this; directly add alphas to the mask.");
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
   * void MaskAdditiveBlitter::blitAntiH(int x, int y, SkAlpha alpha) {
   *     SkASSERT(x >= fMask.fBounds.fLeft - 1);
   *     add_alpha(&this->getRow(y)[x], alpha);
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
   * void MaskAdditiveBlitter::blitAntiH(int x, int y, int width, SkAlpha alpha) {
   *     SkASSERT(x >= fMask.fBounds.fLeft - 1);
   *     uint8_t* row = this->getRow(y);
   *     for (int i = 0; i < width; ++i) {
   *         add_alpha(&row[x + i], alpha);
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
   * void MaskAdditiveBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     if (alpha == 0) {
   *         return;
   *     }
   *     SkASSERT(x >= fMask.fBounds.fLeft - 1);
   *     // This must be called as if this is a real blitter.
   *     // So we directly set alpha rather than adding it.
   *     uint8_t* row = this->getRow(y);
   *     for (int i = 0; i < height; ++i) {
   *         row[x] = alpha;
   *         row += fMask.fRowBytes;
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
   * void MaskAdditiveBlitter::blitRect(int x, int y, int width, int height) {
   *     SkASSERT(x >= fMask.fBounds.fLeft - 1);
   *     // This must be called as if this is a real blitter.
   *     // So we directly set alpha rather than adding it.
   *     uint8_t* row = this->getRow(y);
   *     for (int i = 0; i < height; ++i) {
   *         memset(row + x, 0xFF, width);
   *         row += fMask.fRowBytes;
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
   * void MaskAdditiveBlitter::blitAntiRect(int     x,
   *                                        int     y,
   *                                        int     width,
   *                                        int     height,
   *                                        SkAlpha leftAlpha,
   *                                        SkAlpha rightAlpha) {
   *     blitV(x, y, height, leftAlpha);
   *     blitV(x + 1 + width, y, height, rightAlpha);
   *     blitRect(x + 1, y, width, height);
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
   * void flush_if_y_changed(SkFixed y, SkFixed nextY) override {}
   * ```
   */
  public override fun flushIfYChanged(y: SkFixed, nextY: SkFixed) {
    TODO("Implement flushIfYChanged")
  }

  /**
   * C++ original:
   * ```cpp
   * int getWidth() override { return fClipRect.width(); }
   * ```
   */
  public override fun getWidth(): Int {
    TODO("Implement getWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t* getRow(int y) {
   *         if (y != fY) {
   *             fY   = y;
   *             fRow = fMask.image() + (y - fMask.fBounds.fTop) * fMask.fRowBytes - fMask.fBounds.fLeft;
   *         }
   *         return fRow;
   *     }
   * ```
   */
  public fun getRow(y: Int): Int {
    TODO("Implement getRow")
  }

  public companion object {
    private val kMAXWIDTH: Int = TODO("Initialize kMAXWIDTH")

    private val kMAXSTORAGE: Int = TODO("Initialize kMAXSTORAGE")

    /**
     * C++ original:
     * ```cpp
     * static bool CanHandleRect(const SkIRect& bounds) {
     *         int width = bounds.width();
     *         if (width > MaskAdditiveBlitter::kMAX_WIDTH) {
     *             return false;
     *         }
     *         int64_t rb = SkAlign4(width);
     *         // use 64bits to detect overflow
     *         int64_t storage = rb * bounds.height();
     *
     *         return (width <= MaskAdditiveBlitter::kMAX_WIDTH) &&
     *                (storage <= MaskAdditiveBlitter::kMAX_STORAGE);
     *     }
     * ```
     */
    public fun canHandleRect(bounds: SkIRect): Boolean {
      TODO("Implement canHandleRect")
    }
  }
}
