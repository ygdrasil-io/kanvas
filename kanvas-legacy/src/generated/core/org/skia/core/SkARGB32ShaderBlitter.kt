package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.foundation.SkPMColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkARGB32_Shader_Blitter : public SkShaderBlitter {
 * public:
 *     SkARGB32_Shader_Blitter(const SkPixmap& device, const SkPaint& paint,
 *                             SkShaderBase::Context* shaderContext);
 *     ~SkARGB32_Shader_Blitter() override;
 *     void blitH(int x, int y, int width) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitAntiH(int x, int y, const SkAlpha[], const int16_t[]) override;
 *     void blitMask(const SkMask&, const SkIRect&) override;
 *
 * private:
 *     SkPMColor*          fBuffer;
 *     SkBlitRow::Proc32   fProc32;
 *     SkBlitRow::Proc32   fProc32Blend;
 *     bool fShadeDirectlyIntoDevice;
 * }
 * ```
 */
public open class SkARGB32ShaderBlitter public constructor(
  device: SkPixmap,
  paint: SkPaint,
  shaderContext: SkShaderBase.Context?,
) : SkShaderBlitter(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPMColor*          fBuffer
   * ```
   */
  private var fBuffer: SkPMColor? = TODO("Initialize fBuffer")

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
   * SkBlitRow::Proc32   fProc32Blend
   * ```
   */
  private var fProc32Blend: SkBlitRowProc32 = TODO("Initialize fProc32Blend")

  /**
   * C++ original:
   * ```cpp
   * bool fShadeDirectlyIntoDevice
   * ```
   */
  private var fShadeDirectlyIntoDevice: Boolean = TODO("Initialize fShadeDirectlyIntoDevice")

  /**
   * C++ original:
   * ```cpp
   * void SkARGB32_Shader_Blitter::blitH(int x, int y, int width) {
   *     SkASSERT(x >= 0 && y >= 0 && x + width <= fDevice.width());
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *
   *     if (fShadeDirectlyIntoDevice) {
   *         fShaderContext->shadeSpan(x, y, device, width);
   *     } else {
   *         SkPMColor*  span = fBuffer;
   *         fShaderContext->shadeSpan(x, y, span, width);
   *         fProc32(device, span, width, 255);
   *     }
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
   * void SkARGB32_Shader_Blitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     SkASSERT(x >= 0 && y >= 0 && y + height <= fDevice.height());
   *
   *     uint32_t* device = fDevice.writable_addr32(x, y);
   *     const size_t deviceRB = fDevice.rowBytes();
   *
   *     if (fShadeDirectlyIntoDevice) {
   *         if (255 == alpha) {
   *             do {
   *                 fShaderContext->shadeSpan(x, y, device, 1);
   *                 y += 1;
   *                 device = (uint32_t*)((char*)device + deviceRB);
   *             } while (--height > 0);
   *         } else {
   *             do {
   *                 SkPMColor c;
   *                 fShaderContext->shadeSpan(x, y, &c, 1);
   *                 *device = SkFourByteInterp(c, *device, alpha);
   *                 y += 1;
   *                 device = (uint32_t*)((char*)device + deviceRB);
   *             } while (--height > 0);
   *         }
   *     } else {
   *         SkPMColor* span = fBuffer;
   *         SkBlitRow::Proc32 proc = (255 == alpha) ? fProc32 : fProc32Blend;
   *         do {
   *             fShaderContext->shadeSpan(x, y, span, 1);
   *             proc(device, span, 1, alpha);
   *             y += 1;
   *             device = (uint32_t*)((char*)device + deviceRB);
   *         } while (--height > 0);
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
   * void SkARGB32_Shader_Blitter::blitRect(int x, int y, int width, int height) {
   *     SkASSERT(x >= 0 && y >= 0 &&
   *              x + width <= fDevice.width() && y + height <= fDevice.height());
   *
   *     uint32_t*  device = fDevice.writable_addr32(x, y);
   *     const size_t deviceRB = fDevice.rowBytes();
   *     auto*      shaderContext = fShaderContext;
   *     SkPMColor* span = fBuffer;
   *
   *     if (fShadeDirectlyIntoDevice) {
   *         do {
   *             shaderContext->shadeSpan(x, y, device, width);
   *             y += 1;
   *             device = (uint32_t*)((char*)device + deviceRB);
   *         } while (--height > 0);
   *     } else {
   *         SkBlitRow::Proc32 proc = fProc32;
   *         do {
   *             shaderContext->shadeSpan(x, y, span, width);
   *             proc(device, span, width, 255);
   *             y += 1;
   *             device = (uint32_t*)((char*)device + deviceRB);
   *         } while (--height > 0);
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
   * void SkARGB32_Shader_Blitter::blitAntiH(int x, int y, const SkAlpha antialias[],
   *                                         const int16_t runs[]) {
   *     SkPMColor* span = fBuffer;
   *     uint32_t*  device = fDevice.writable_addr32(x, y);
   *     auto*      shaderContext = fShaderContext;
   *
   *     if (fShadeDirectlyIntoDevice || (shaderContext->getFlags() & SkShaderBase::kOpaqueAlpha_Flag)) {
   *         for (;;) {
   *             int count = *runs;
   *             if (count <= 0) {
   *                 break;
   *             }
   *             int aa = *antialias;
   *             if (aa) {
   *                 if (aa == 255) {
   *                     // cool, have the shader draw right into the device
   *                     shaderContext->shadeSpan(x, y, device, count);
   *                 } else {
   *                     shaderContext->shadeSpan(x, y, span, count);
   *                     fProc32Blend(device, span, count, aa);
   *                 }
   *             }
   *             device += count;
   *             runs += count;
   *             antialias += count;
   *             x += count;
   *         }
   *     } else {
   *         for (;;) {
   *             int count = *runs;
   *             if (count <= 0) {
   *                 break;
   *             }
   *             int aa = *antialias;
   *             if (aa) {
   *                 shaderContext->shadeSpan(x, y, span, count);
   *                 if (aa == 255) {
   *                     fProc32(device, span, count, 255);
   *                 } else {
   *                     fProc32Blend(device, span, count, aa);
   *                 }
   *             }
   *             device += count;
   *             runs += count;
   *             antialias += count;
   *             x += count;
   *         }
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
   * void SkARGB32_Shader_Blitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *
   *     void (*blend_row)(SkPMColor*, const void* mask, const SkPMColor*, int) = nullptr;
   *
   *     bool opaque = (fShaderContext->getFlags() & SkShaderBase::kOpaqueAlpha_Flag);
   *
   *     if (mask.fFormat == SkMask::kA8_Format && opaque) {
   *         blend_row = blend_row_A8_opaque;
   *     } else if (mask.fFormat == SkMask::kA8_Format) {
   *         blend_row = blend_row_A8;
   *     } else if (mask.fFormat == SkMask::kLCD16_Format && opaque) {
   *         blend_row = blend_row_LCD16_opaque;
   *     } else if (mask.fFormat == SkMask::kLCD16_Format) {
   *         blend_row = blend_row_lcd16;
   *     } else {
   *         this->SkShaderBlitter::blitMask(mask, clip);
   *         return;
   *     }
   *
   *     const int x = clip.fLeft;
   *     const int width = clip.width();
   *     int y = clip.fTop;
   *     int height = clip.height();
   *
   *     char* dstRow = (char*)fDevice.writable_addr32(x, y);
   *     const size_t dstRB = fDevice.rowBytes();
   *     const uint8_t* maskRow = (const uint8_t*)mask.getAddr(x, y);
   *     const size_t maskRB = mask.fRowBytes;
   *
   *     SkPMColor* span = fBuffer;
   *     SkASSERT(blend_row);
   *     do {
   *         fShaderContext->shadeSpan(x, y, span, width);
   *         blend_row(reinterpret_cast<SkPMColor*>(dstRow), maskRow, span, width);
   *         dstRow += dstRB;
   *         maskRow += maskRB;
   *         y += 1;
   *     } while (--height > 0);
   * }
   * ```
   */
  public override fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }
}
