package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkA8_Blitter : public SkBlitter {
 * public:
 *     SkA8_Blitter(const SkPixmap& device, const SkPaint& paint);
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitMask(const SkMask&, const SkIRect&) override;
 *
 * private:
 *     const SkPixmap  fDevice;
 *     AlphaProc       fOneProc;
 *     A8_RowBlitBW    fBWProc;
 *     A8_RowBlitAA    fAAProc;
 *     SkAlpha         fSrc;
 *
 *     using INHERITED = SkBlitter;
 * }
 * ```
 */
public open class SkA8Blitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
) : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * const SkPixmap  fDevice
   * ```
   */
  private val fDevice: SkPixmap = TODO("Initialize fDevice")

  /**
   * C++ original:
   * ```cpp
   * AlphaProc       fOneProc
   * ```
   */
  private var fOneProc: AlphaProc = TODO("Initialize fOneProc")

  /**
   * C++ original:
   * ```cpp
   * A8_RowBlitBW    fBWProc
   * ```
   */
  private var fBWProc: A8RowBlitBW = TODO("Initialize fBWProc")

  /**
   * C++ original:
   * ```cpp
   * A8_RowBlitAA    fAAProc
   * ```
   */
  private var fAAProc: A8RowBlitAA = TODO("Initialize fAAProc")

  /**
   * C++ original:
   * ```cpp
   * SkAlpha         fSrc
   * ```
   */
  private var fSrc: SkAlpha = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * void SkA8_Blitter::blitH(int x, int y, int width) {
   *     fBWProc(fDevice.writable_addr8(x, y), fSrc, width);
   * }
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
   * void SkA8_Blitter::blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) {
   *     uint8_t* device = fDevice.writable_addr8(x, y);
   *     SkDEBUGCODE(int totalCount = 0;)
   *
   *     for (;;) {
   *         int count = runs[0];
   *         SkASSERT(count >= 0);
   *         if (count == 0) {
   *             return;
   *         }
   *
   *         if (antialias[0] == 0xFF) {
   *             fBWProc(device, fSrc, count);
   *         } else if (antialias[0] != 0) {
   *             fAAProc(device, fSrc, count, antialias[0]);
   *         }
   *
   *         runs += count;
   *         antialias += count;
   *         device += count;
   *
   *         SkDEBUGCODE(totalCount += count;)
   *     }
   *     SkASSERT(fDevice.width() == totalCount);
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkA8_Blitter::blitV(int x, int y, int height, SkAlpha aa) {
   *     uint8_t* device = fDevice.writable_addr8(x, y);
   *     const size_t dstRB = fDevice.rowBytes();
   *
   *     if (aa == 0xFF) {
   *         while (--height >= 0) {
   *             *device = fOneProc(fSrc, *device);
   *             device += dstRB;
   *         }
   *     } else if (aa != 0) {
   *         while (--height >= 0) {
   *             fAAProc(device, fSrc, 1, aa);
   *             device += dstRB;
   *         }
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
   * void SkA8_Blitter::blitRect(int x, int y, int width, int height) {
   *     uint8_t* device = fDevice.writable_addr8(x, y);
   *     const size_t dstRB = fDevice.rowBytes();
   *
   *     while (--height >= 0) {
   *         fBWProc(device, fSrc, width);
   *         device += dstRB;
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
   * void SkA8_Blitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     if (SkMask::kA8_Format != mask.fFormat) {
   *         this->INHERITED::blitMask(mask, clip);
   *         return;
   *     }
   *
   *     int x = clip.fLeft;
   *     int y = clip.fTop;
   *     int width = clip.width();
   *     int height = clip.height();
   *
   *     uint8_t* dst = fDevice.writable_addr8(x, y);
   *     const uint8_t* src = mask.getAddr8(x, y);
   *     const size_t srcRB = mask.fRowBytes;
   *     const size_t dstRB = fDevice.rowBytes();
   *
   *     while (--height >= 0) {
   *         for (int i = 0; i < width; ++i) {
   *             dst[i] = u8_lerp(dst[i], fOneProc(fSrc, dst[i]), src[i]);
   *         }
   *         dst += dstRB;
   *         src += srcRB;
   *     }
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }
}
