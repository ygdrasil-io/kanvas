package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.memory.SkArenaAlloc
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkRasterPipelineSpriteBlitter : public SkSpriteBlitter {
 * public:
 *     SkRasterPipelineSpriteBlitter(const SkPixmap& src, SkArenaAlloc* alloc,
 *                                   sk_sp<SkShader> clipShader)
 *         : INHERITED(src)
 *         , fAlloc(alloc)
 *         , fBlitter(nullptr)
 *         , fSrcPtr{nullptr, 0}
 *         , fClipShader(std::move(clipShader))
 *     {}
 *
 *     bool setup(const SkPixmap& dst, int left, int top, const SkPaint& paint) override {
 *         fDst  = dst;
 *         fLeft = left;
 *         fTop  = top;
 *         fPaintColor = paint.getColor4f();
 *
 *         SkRasterPipeline p(fAlloc);
 *         p.appendLoad(fSource.colorType(), &fSrcPtr);
 *
 *         if (SkColorTypeIsAlphaOnly(fSource.colorType())) {
 *             // The color for A8 images comes from the (sRGB) paint color.
 *             p.appendSetRGB(fAlloc, fPaintColor);
 *             p.append(SkRasterPipelineOp::premul);
 *         }
 *         if (auto dstCS = fDst.colorSpace()) {
 *             auto srcCS = fSource.colorSpace();
 *             if (!srcCS || SkColorTypeIsAlphaOnly(fSource.colorType())) {
 *                 // We treat untagged images as sRGB.
 *                 // Alpha-only images get their r,g,b from the paint color, so they're also sRGB.
 *                 srcCS = sk_srgb_singleton();
 *             }
 *             auto srcAT = fSource.isOpaque() ? kOpaque_SkAlphaType
 *                                             : kPremul_SkAlphaType;
 *             fAlloc->make<SkColorSpaceXformSteps>(srcCS, srcAT,
 *                                                  dstCS, kPremul_SkAlphaType)
 *                 ->apply(&p);
 *         }
 *         if (fPaintColor.fA != 1.0f) {
 *             p.append(SkRasterPipelineOp::scale_1_float, &fPaintColor.fA);
 *         }
 *
 *         bool is_opaque = fSource.isOpaque() && fPaintColor.fA == 1.0f;
 *         fBlitter = SkCreateRasterPipelineBlitter(fDst, paint, p, is_opaque, fAlloc, fClipShader);
 *         return fBlitter != nullptr;
 *     }
 *
 *     void blitRect(int x, int y, int width, int height) override {
 *         fSrcPtr.stride = fSource.rowBytesAsPixels();
 *
 *         // We really want fSrcPtr.pixels = fSource.addr(-fLeft, -fTop) here, but that asserts.
 *         // Instead we ask for addr(-fLeft+x, -fTop+y), then back up (x,y) manually.
 *         // Representing bpp as a size_t keeps all this math in size_t instead of int,
 *         // which could wrap around with large enough fSrcPtr.stride and y.
 *         size_t bpp = fSource.info().bytesPerPixel();
 *         fSrcPtr.pixels = (char*)fSource.writable_addr(-fLeft+x, -fTop+y) - bpp * x
 *                                                                          - bpp * y * fSrcPtr.stride;
 *
 *         fBlitter->blitRect(x,y,width,height);
 *     }
 *
 * private:
 *     SkArenaAlloc*              fAlloc;
 *     SkBlitter*                 fBlitter;
 *     SkRasterPipelineContexts::MemoryCtx fSrcPtr;
 *     SkColor4f                  fPaintColor;
 *     sk_sp<SkShader>            fClipShader;
 *
 *     using INHERITED = SkSpriteBlitter;
 * }
 * ```
 */
public open class SkRasterPipelineSpriteBlitter public constructor(
  src: SkPixmap,
  alloc: SkArenaAlloc?,
  clipShader: SkSp<SkShader>,
) : SkSpriteBlitter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc*              fAlloc
   * ```
   */
  private var fAlloc: SkArenaAlloc? = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SkBlitter*                 fBlitter
   * ```
   */
  private var fBlitter: SkBlitter? = TODO("Initialize fBlitter")

  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::MemoryCtx fSrcPtr
   * ```
   */
  private var fSrcPtr: MemoryCtx = TODO("Initialize fSrcPtr")

  /**
   * C++ original:
   * ```cpp
   * SkColor4f                  fPaintColor
   * ```
   */
  private var fPaintColor: SkColor4f = TODO("Initialize fPaintColor")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>            fClipShader
   * ```
   */
  private var fClipShader: SkSp<SkShader> = TODO("Initialize fClipShader")

  /**
   * C++ original:
   * ```cpp
   * bool setup(const SkPixmap& dst, int left, int top, const SkPaint& paint) override {
   *         fDst  = dst;
   *         fLeft = left;
   *         fTop  = top;
   *         fPaintColor = paint.getColor4f();
   *
   *         SkRasterPipeline p(fAlloc);
   *         p.appendLoad(fSource.colorType(), &fSrcPtr);
   *
   *         if (SkColorTypeIsAlphaOnly(fSource.colorType())) {
   *             // The color for A8 images comes from the (sRGB) paint color.
   *             p.appendSetRGB(fAlloc, fPaintColor);
   *             p.append(SkRasterPipelineOp::premul);
   *         }
   *         if (auto dstCS = fDst.colorSpace()) {
   *             auto srcCS = fSource.colorSpace();
   *             if (!srcCS || SkColorTypeIsAlphaOnly(fSource.colorType())) {
   *                 // We treat untagged images as sRGB.
   *                 // Alpha-only images get their r,g,b from the paint color, so they're also sRGB.
   *                 srcCS = sk_srgb_singleton();
   *             }
   *             auto srcAT = fSource.isOpaque() ? kOpaque_SkAlphaType
   *                                             : kPremul_SkAlphaType;
   *             fAlloc->make<SkColorSpaceXformSteps>(srcCS, srcAT,
   *                                                  dstCS, kPremul_SkAlphaType)
   *                 ->apply(&p);
   *         }
   *         if (fPaintColor.fA != 1.0f) {
   *             p.append(SkRasterPipelineOp::scale_1_float, &fPaintColor.fA);
   *         }
   *
   *         bool is_opaque = fSource.isOpaque() && fPaintColor.fA == 1.0f;
   *         fBlitter = SkCreateRasterPipelineBlitter(fDst, paint, p, is_opaque, fAlloc, fClipShader);
   *         return fBlitter != nullptr;
   *     }
   * ```
   */
  public override fun setup(
    dst: SkPixmap,
    left: Int,
    top: Int,
    paint: SkPaint,
  ): Boolean {
    TODO("Implement setup")
  }

  /**
   * C++ original:
   * ```cpp
   * void blitRect(int x, int y, int width, int height) override {
   *         fSrcPtr.stride = fSource.rowBytesAsPixels();
   *
   *         // We really want fSrcPtr.pixels = fSource.addr(-fLeft, -fTop) here, but that asserts.
   *         // Instead we ask for addr(-fLeft+x, -fTop+y), then back up (x,y) manually.
   *         // Representing bpp as a size_t keeps all this math in size_t instead of int,
   *         // which could wrap around with large enough fSrcPtr.stride and y.
   *         size_t bpp = fSource.info().bytesPerPixel();
   *         fSrcPtr.pixels = (char*)fSource.writable_addr(-fLeft+x, -fTop+y) - bpp * x
   *                                                                          - bpp * y * fSrcPtr.stride;
   *
   *         fBlitter->blitRect(x,y,width,height);
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
