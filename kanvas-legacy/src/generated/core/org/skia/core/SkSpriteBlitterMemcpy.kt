package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap

/**
 * C++ original:
 * ```cpp
 * class SkSpriteBlitter_Memcpy final : public SkSpriteBlitter {
 * public:
 *     static bool Supports(const SkPixmap& dst, const SkPixmap& src, const SkPaint& paint) {
 *         // the caller has already inspected the colorspace on src and dst
 *         SkASSERT(0 == SkColorSpaceXformSteps(src,dst).fFlags.mask());
 *
 *         if (dst.colorType() != src.colorType()) {
 *             return false;
 *         }
 *         if (paint.getMaskFilter() || paint.getColorFilter() || paint.getImageFilter()) {
 *             return false;
 *         }
 *         if (0xFF != paint.getAlpha()) {
 *             return false;
 *         }
 *         const auto mode = paint.asBlendMode();
 *         return mode == SkBlendMode::kSrc || (mode == SkBlendMode::kSrcOver && src.isOpaque());
 *     }
 *
 *     SkSpriteBlitter_Memcpy(const SkPixmap& src)
 *         : INHERITED(src) {}
 *
 *     void blitRect(int x, int y, int width, int height) override {
 *         SkASSERT(fDst.colorType() == fSource.colorType());
 *         SkASSERT(width > 0 && height > 0);
 *
 *         char* dst = (char*)fDst.writable_addr(x, y);
 *         const char* src = (const char*)fSource.addr(x - fLeft, y - fTop);
 *         const size_t dstRB = fDst.rowBytes();
 *         const size_t srcRB = fSource.rowBytes();
 *         const size_t bytesToCopy = width << fSource.shiftPerPixel();
 *
 *         while (height --> 0) {
 *             memcpy(dst, src, bytesToCopy);
 *             dst += dstRB;
 *             src += srcRB;
 *         }
 *     }
 *
 * private:
 *     using INHERITED = SkSpriteBlitter;
 * }
 * ```
 */
public class SkSpriteBlitterMemcpy public constructor(
  src: SkPixmap,
) : SkSpriteBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void blitRect(int x, int y, int width, int height) override {
   *         SkASSERT(fDst.colorType() == fSource.colorType());
   *         SkASSERT(width > 0 && height > 0);
   *
   *         char* dst = (char*)fDst.writable_addr(x, y);
   *         const char* src = (const char*)fSource.addr(x - fLeft, y - fTop);
   *         const size_t dstRB = fDst.rowBytes();
   *         const size_t srcRB = fSource.rowBytes();
   *         const size_t bytesToCopy = width << fSource.shiftPerPixel();
   *
   *         while (height --> 0) {
   *             memcpy(dst, src, bytesToCopy);
   *             dst += dstRB;
   *             src += srcRB;
   *         }
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

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Supports(const SkPixmap& dst, const SkPixmap& src, const SkPaint& paint) {
     *         // the caller has already inspected the colorspace on src and dst
     *         SkASSERT(0 == SkColorSpaceXformSteps(src,dst).fFlags.mask());
     *
     *         if (dst.colorType() != src.colorType()) {
     *             return false;
     *         }
     *         if (paint.getMaskFilter() || paint.getColorFilter() || paint.getImageFilter()) {
     *             return false;
     *         }
     *         if (0xFF != paint.getAlpha()) {
     *             return false;
     *         }
     *         const auto mode = paint.asBlendMode();
     *         return mode == SkBlendMode::kSrc || (mode == SkBlendMode::kSrcOver && src.isOpaque());
     *     }
     * ```
     */
    public fun supports(
      dst: SkPixmap,
      src: SkPixmap,
      paint: SkPaint,
    ): Boolean {
      TODO("Implement supports")
    }
  }
}
