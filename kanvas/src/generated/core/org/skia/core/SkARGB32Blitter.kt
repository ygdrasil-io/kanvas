package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkColor
import org.skia.foundation.SkMask
import org.skia.foundation.SkPMColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.U8CPU
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkARGB32_Blitter : public SkRasterBlitter {
 * public:
 *     SkARGB32_Blitter(const SkPixmap& device, const SkPaint& paint);
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitMask(const SkMask&, const SkIRect&) override;
 *     void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) override;
 *     void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) override;
 *
 * protected:
 *     SkColor   fColor;
 *     SkPMColor fPMColor;
 *     SkAlpha   fSrcA;
 * }
 * ```
 */
public open class SkARGB32Blitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
) : SkRasterBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkColor   fColor
   * ```
   */
  protected var fColor: SkColor = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * SkPMColor fPMColor
   * ```
   */
  protected var fPMColor: SkPMColor = TODO("Initialize fPMColor")

  /**
   * C++ original:
   * ```cpp
   * SkAlpha   fSrcA
   * ```
   */
  protected var fSrcA: SkAlpha = TODO("Initialize fSrcA")

  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Blitter::blitH(int x, int y, int width) {
   *     SkASSERT(x >= 0 && y >= 0 && x + width <= fDevice.width());
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkBlitRow::Color32(device, width, fPMColor);
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
   * void SkARGB32_Blitter::blitAntiH(int x, int y, const SkAlpha antialias[],
   *                                  const int16_t runs[]) {
   *     SkASSERT(fSrcA != 0xFF);  // There is an opaque specialization
   *     if (fSrcA == 0) {
   *         return;
   *     }
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *
   *     for (;;) {
   *         int count = runs[0];
   *         SkASSERT(count >= 0);
   *         if (count <= 0) {
   *             return;
   *         }
   *         SkAlpha aa = antialias[0];
   *         if (aa) {
   *             SkPMColor sc = SkAlphaMulQ(fPMColor, SkAlpha255To256(aa));
   *             SkBlitRow::Color32(device, count, sc);
   *         }
   *         runs += count;
   *         antialias += count;
   *         device += count;
   *     }
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
   * void SkARGB32_Blitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     if (alpha == 0 || fSrcA == 0) {
   *         return;
   *     }
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkPMColor color = fPMColor;
   *
   *     if (alpha != 255) {
   *         color = SkAlphaMulQ(color, SkAlpha255To256(alpha));
   *     }
   *
   *     const unsigned dst_scale = SkAlpha255To256(255 - SkGetPackedA32(color));
   *     const size_t rowBytes = fDevice.rowBytes();
   *     while (--height >= 0) {
   *         device[0] = color + SkAlphaMulQ(device[0], dst_scale);
   *         device = (uint32_t*)((char*)device + rowBytes);
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
   * void SkARGB32_Blitter::blitRect(int x, int y, int width, int height) {
   *     SkASSERT(x >= 0 && y >= 0 && x + width <= fDevice.width() && y + height <= fDevice.height());
   *
   *     if (fSrcA == 0) {
   *         return;
   *     }
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     const size_t rowBytes = fDevice.rowBytes();
   *
   *     if (SkGetPackedA32(fPMColor) == 0xFF) {
   *         SkOpts::rect_memset32(device, fPMColor, width, rowBytes, height);
   *     } else {
   *         while (height --> 0) {
   *             SkBlitRow::Color32(device, width, fPMColor);
   *             device = (uint32_t*)((char*)device + rowBytes);
   *         }
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
   * void SkARGB32_Blitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *     SkASSERT(fSrcA != 0xFF);
   *
   *     if (fSrcA == 0) {
   *         return;
   *     }
   *
   *     if (blit_color(fDevice, mask, clip, fColor)) {
   *         return;
   *     }
   *
   *     switch (mask.fFormat) {
   *         case SkMask::kBW_Format:
   *             SkARGB32_BlendBW(fDevice, mask, clip, fPMColor, SkAlpha255To256(255 - fSrcA));
   *             break;
   *         case SkMask::kARGB32_Format:
   *             SkARGB32_Blit32(fDevice, mask, clip, fPMColor);
   *             break;
   *         default:
   *             SK_ABORT("Mask format not handled.");
   *     }
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Blitter::blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x + 1, y);)
   *
   *     device[0] = SkBlendARGB32(fPMColor, device[0], a0);
   *     device[1] = SkBlendARGB32(fPMColor, device[1], a1);
   * }
   * ```
   */
  public override fun blitAntiH2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiH2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Blitter::blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x, y + 1);)
   *
   *     device[0] = SkBlendARGB32(fPMColor, device[0], a0);
   *     device = (uint32_t*)((char*)device + fDevice.rowBytes());
   *     device[0] = SkBlendARGB32(fPMColor, device[0], a1);
   * }
   * ```
   */
  public override fun blitAntiV2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiV2")
  }
}
