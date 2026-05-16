package org.skia.core

import kotlin.Int
import org.skia.foundation.SkPixmap
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * class Sprite_D32_S32 : public SkSpriteBlitter {
 * public:
 *     Sprite_D32_S32(const SkPixmap& src, U8CPU alpha)  : INHERITED(src) {
 *         SkASSERT(src.colorType() == kN32_SkColorType);
 *
 *         unsigned flags32 = 0;
 *         if (255 != alpha) {
 *             flags32 |= SkBlitRow::kGlobalAlpha_Flag32;
 *         }
 *         if (!src.isOpaque()) {
 *             flags32 |= SkBlitRow::kSrcPixelAlpha_Flag32;
 *         }
 *
 *         fProc32 = SkBlitRow::Factory32(flags32);
 *         fAlpha = alpha;
 *     }
 *
 *     void blitRect(int x, int y, int width, int height) override {
 *         SkASSERT(width > 0 && height > 0);
 *         uint32_t* SK_RESTRICT dst = fDst.writable_addr32(x, y);
 *         const uint32_t* SK_RESTRICT src = fSource.addr32(x - fLeft, y - fTop);
 *         size_t dstRB = fDst.rowBytes();
 *         size_t srcRB = fSource.rowBytes();
 *         SkBlitRow::Proc32 proc = fProc32;
 *         U8CPU             alpha = fAlpha;
 *
 *         do {
 *             proc(dst, src, width, alpha);
 *             dst = (uint32_t* SK_RESTRICT)((char*)dst + dstRB);
 *             src = (const uint32_t* SK_RESTRICT)((const char*)src + srcRB);
 *         } while (--height != 0);
 *     }
 *
 * private:
 *     SkBlitRow::Proc32   fProc32;
 *     U8CPU               fAlpha;
 *
 *     using INHERITED = SkSpriteBlitter;
 * }
 * ```
 */
public open class SpriteD32S32 public constructor(
  src: SkPixmap,
  alpha: U8CPU,
) : SkSpriteBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkBlitRow::Proc32   fProc32
   * ```
   */
  private var fProc32: SkBlitRowProc32 = TODO("Initialize fProc32")

  /**
   * C++ original:
   * ```cpp
   * U8CPU               fAlpha
   * ```
   */
  private var fAlpha: U8CPU = TODO("Initialize fAlpha")

  /**
   * C++ original:
   * ```cpp
   * void blitRect(int x, int y, int width, int height) override {
   *         SkASSERT(width > 0 && height > 0);
   *         uint32_t* SK_RESTRICT dst = fDst.writable_addr32(x, y);
   *         const uint32_t* SK_RESTRICT src = fSource.addr32(x - fLeft, y - fTop);
   *         size_t dstRB = fDst.rowBytes();
   *         size_t srcRB = fSource.rowBytes();
   *         SkBlitRow::Proc32 proc = fProc32;
   *         U8CPU             alpha = fAlpha;
   *
   *         do {
   *             proc(dst, src, width, alpha);
   *             dst = (uint32_t* SK_RESTRICT)((char*)dst + dstRB);
   *             src = (const uint32_t* SK_RESTRICT)((const char*)src + srcRB);
   *         } while (--height != 0);
   *     }
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
}
