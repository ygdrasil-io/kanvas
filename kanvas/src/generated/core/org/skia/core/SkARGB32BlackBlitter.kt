package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * class SkARGB32_Black_Blitter : public SkARGB32_Opaque_Blitter {
 * public:
 *     SkARGB32_Black_Blitter(const SkPixmap& device, const SkPaint& paint)
 *             : SkARGB32_Opaque_Blitter(device, paint) {
 *         SkASSERT(paint.getColor() == SK_ColorBLACK);
 *     }
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override;
 *     void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) override;
 *     void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) override;
 * }
 * ```
 */
public open class SkARGB32BlackBlitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
) : SkARGB32OpaqueBlitter(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Black_Blitter::blitAntiH(int x, int y, const SkAlpha antialias[],
   *                                        const int16_t runs[]) {
   *     uint32_t*   device = fDevice.writable_addr32(x, y);
   *     static constexpr SkPMColor kBlack = (SkPMColor)(SK_A32_MASK << SK_A32_SHIFT);
   *
   *     for (;;) {
   *         int count = runs[0];
   *         SkASSERT(count >= 0);
   *         if (count <= 0) {
   *             return;
   *         }
   *         unsigned aa = antialias[0];
   *         if (aa) {
   *             if (aa == 255) {
   *                 SkOpts::memset32(device, kBlack, count);
   *             } else {
   *                 const SkPMColor src = aa << SK_A32_SHIFT;
   *                 const unsigned dst_scale = SkAlpha255To256(255 - aa);
   *                 int n = count;
   *                 do {
   *                     --n;
   *                     device[n] = src + SkAlphaMulQ(device[n], dst_scale);
   *                 } while (n > 0);
   *             }
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
   * void SkARGB32_Black_Blitter::blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x + 1, y);)
   *
   *     device[0] = (a0 << SK_A32_SHIFT) + SkAlphaMulQ(device[0], 256 - a0);
   *     device[1] = (a1 << SK_A32_SHIFT) + SkAlphaMulQ(device[1], 256 - a1);
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
   * void SkARGB32_Black_Blitter::blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     SkDEBUGCODE((void)fDevice.writable_addr32(x, y + 1);)
   *
   *     device[0] = (a0 << SK_A32_SHIFT) + SkAlphaMulQ(device[0], 256 - a0);
   *     device = (uint32_t*)((char*)device + fDevice.rowBytes());
   *     device[0] = (a1 << SK_A32_SHIFT) + SkAlphaMulQ(device[0], 256 - a1);
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
