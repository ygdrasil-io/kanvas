package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.U8CPU
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkARGB32_Opaque_Blitter : public SkARGB32_Blitter {
 * public:
 *     SkARGB32_Opaque_Blitter(const SkPixmap& device, const SkPaint& paint)
 *             : SkARGB32_Blitter(device, paint) {
 *         SkASSERT(paint.getAlpha() == 0xFF);
 *     }
 *     void blitMask(const SkMask&, const SkIRect&) override;
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override;
 *     void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) override;
 *     void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) override;
 *     std::optional<DirectBlit> canDirectBlit() override;
 * }
 * ```
 */
public open class SkARGB32OpaqueBlitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
) : SkARGB32Blitter(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Opaque_Blitter::blitMask(const SkMask& mask,
   *                                        const SkIRect& clip) {
   *     SkASSERT(fSrcA == 0xFF);
   *     SkASSERT(mask.fBounds.contains(clip));
   *
   *     if (blit_color(fDevice, mask, clip, fColor)) {
   *         return;
   *     }
   *
   *     switch (mask.fFormat) {
   *         case SkMask::kBW_Format:
   *             SkARGB32_BlitBW(fDevice, mask, clip, fPMColor);
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
   * void SkARGB32_Opaque_Blitter::blitAntiH(int x,
   *                                         int y,
   *                                         const SkAlpha antialias[],
   *                                         const int16_t runs[]) {
   *     SkASSERT(fSrcA == 0xFF);
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     for (;;) {
   *         int count = runs[0];
   *         SkASSERT(count >= 0);
   *         if (count <= 0) {
   *             return;
   *         }
   *         SkAlpha aa = antialias[0];
   *         if (aa == 255) {
   *             SkOpts::memset32(device, fPMColor, count);
   *         } else if (aa > 0) {
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
   * void SkARGB32_Opaque_Blitter::blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x + 1, y);)
   *
   *     device[0] = SkFastFourByteInterp(fPMColor, device[0], a0);
   *     device[1] = SkFastFourByteInterp(fPMColor, device[1], a1);
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
   * void SkARGB32_Opaque_Blitter::blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x, y + 1);)
   *
   *     device[0] = SkFastFourByteInterp(fPMColor, device[0], a0);
   *     device = (uint32_t*)((char*)device + fDevice.rowBytes());
   *     device[0] = SkFastFourByteInterp(fPMColor, device[0], a1);
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

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkBlitter::DirectBlit> SkARGB32_Opaque_Blitter::canDirectBlit() {
   *     return {{ fDevice, fPMColor }};
   * }
   * ```
   */
  public override fun canDirectBlit(): Int {
    TODO("Implement canDirectBlit")
  }
}
