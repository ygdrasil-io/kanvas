package org.skia.gpu

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class TiledTextureUtils {
 * public:
 *     static bool ShouldTileImage(SkIRect conservativeClipBounds,
 *                                 const SkISize& imageSize,
 *                                 const SkMatrix& ctm,
 *                                 const SkMatrix& srcToDst,
 *                                 const SkRect* src,
 *                                 int maxTileSize,
 *                                 size_t cacheSize,
 *                                 int* tileSize,
 *                                 SkIRect* clippedSubset);
 *
 *     enum class ImageDrawMode {
 *         // Src and dst have been restricted to the image content. May need to clamp, no need to
 *         // decal.
 *         kOptimized,
 *         // Src and dst are their original sizes, requires use of a decal instead of plain clamping.
 *         // This is used when a dst clip is provided and extends outside of the optimized dst rect.
 *         kDecal,
 *         // Src or dst are empty, or do not intersect the image content so don't draw anything.
 *         kSkip
 *     };
 *
 *     static ImageDrawMode OptimizeSampleArea(const SkISize& imageSize,
 *                                             const SkRect& origSrcRect,
 *                                             const SkRect& origDstRect,
 *                                             const SkPoint dstClip[4],
 *                                             SkRect* outSrcRect,
 *                                             SkRect* outDstRect,
 *                                             SkMatrix* outSrcToDst);
 *
 *     static bool CanDisableMipmap(const SkMatrix& viewM,
 *                                  const SkMatrix& localM,
 *                                  bool sharpenMipmappedTextures);
 *
 *     static void ClampedOutsetWithOffset(SkIRect* iRect, int outset, SkPoint* offset,
 *                                         const SkIRect& clamp);
 *
 *     static std::tuple<bool, size_t> DrawAsTiledImageRect(SkCanvas*,
 *                                                          const SkImage*,
 *                                                          const SkRect& srcRect,
 *                                                          const SkRect& dstRect,
 *                                                          SkCanvas::QuadAAFlags,
 *                                                          const SkSamplingOptions&,
 *                                                          const SkPaint*,
 *                                                          SkCanvas::SrcRectConstraint,
 *                                                          bool sharpenMM,
 *                                                          size_t cacheSize,
 *                                                          size_t maxTextureSize,
 *                                                          bool renderLazyPictureTilesOnGPU = true);
 * }
 * ```
 */
public open class TiledTextureUtils {
  public enum class ImageDrawMode {
    kOptimized,
    kDecal,
    kSkip,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool TiledTextureUtils::ShouldTileImage(SkIRect conservativeClipBounds,
     *                                         const SkISize& imageSize,
     *                                         const SkMatrix& ctm,
     *                                         const SkMatrix& srcToDst,
     *                                         const SkRect* src,
     *                                         int maxTileSize,
     *                                         size_t cacheSize,
     *                                         int* tileSize,
     *                                         SkIRect* clippedSubset) {
     *     // if it's larger than the max tile size, then we have no choice but tiling.
     *     if (imageSize.width() > maxTileSize || imageSize.height() > maxTileSize) {
     *         *clippedSubset = determine_clipped_src_rect(conservativeClipBounds, ctm,
     *                                                     srcToDst, imageSize, src);
     *         *tileSize = determine_tile_size(*clippedSubset, maxTileSize);
     *         return true;
     *     }
     *
     *     // If the image would only produce 4 tiles of the smaller size, don't bother tiling it.
     *     const size_t area = imageSize.width() * imageSize.height();
     *     if (area < 4 * kBmpSmallTileSize * kBmpSmallTileSize) {
     *         return false;
     *     }
     *
     *     // At this point we know we could do the draw by uploading the entire bitmap as a texture.
     *     // However, if the texture would be large compared to the cache size and we don't require most
     *     // of it for this draw then tile to reduce the amount of upload and cache spill.
     *     if (!cacheSize) {
     *         // We don't have access to the cacheSize so we will just upload the entire image
     *         // to be on the safe side and not tile.
     *         return false;
     *     }
     *
     *     // An assumption here is that sw bitmap size is a good proxy for its size as a texture
     *     size_t bmpSize = area * sizeof(SkPMColor);  // assume 32bit pixels
     *     if (bmpSize < cacheSize / 2) {
     *         return false;
     *     }
     *
     *     // Figure out how much of the src we will need based on the src rect and clipping. Reject if
     *     // tiling memory savings would be < 50%.
     *     *clippedSubset = determine_clipped_src_rect(conservativeClipBounds, ctm,
     *                                                 srcToDst, imageSize, src);
     *     *tileSize = kBmpSmallTileSize; // already know whole bitmap fits in one max sized tile.
     *     size_t usedTileBytes = get_tile_count(*clippedSubset, kBmpSmallTileSize) *
     *                            kBmpSmallTileSize * kBmpSmallTileSize *
     *                            sizeof(SkPMColor);  // assume 32bit pixels;
     *
     *     return usedTileBytes * 2 < bmpSize;
     * }
     * ```
     */
    public fun shouldTileImage(
      conservativeClipBounds: SkIRect,
      imageSize: SkISize,
      ctm: SkMatrix,
      srcToDst: SkMatrix,
      src: SkRect?,
      maxTileSize: Int,
      cacheSize: ULong,
      tileSize: Int?,
      clippedSubset: SkIRect?,
    ): Boolean {
      TODO("Implement shouldTileImage")
    }

    /**
     * C++ original:
     * ```cpp
     * TiledTextureUtils::ImageDrawMode TiledTextureUtils::OptimizeSampleArea(const SkISize& imageSize,
     *                                                                        const SkRect& origSrcRect,
     *                                                                        const SkRect& origDstRect,
     *                                                                        const SkPoint dstClip[4],
     *                                                                        SkRect* outSrcRect,
     *                                                                        SkRect* outDstRect,
     *                                                                        SkMatrix* outSrcToDst) {
     *     if (origSrcRect.isEmpty() || origDstRect.isEmpty()) {
     *         return ImageDrawMode::kSkip;
     *     }
     *
     *     *outSrcToDst = SkMatrix::RectToRectOrIdentity(origSrcRect, origDstRect);
     *
     *     SkRect src = origSrcRect;
     *     SkRect dst = origDstRect;
     *
     *     const SkRect srcBounds = SkRect::Make(imageSize);
     *
     *     if (!srcBounds.contains(src)) {
     *         if (!src.intersect(srcBounds)) {
     *             return ImageDrawMode::kSkip;
     *         }
     *         outSrcToDst->mapRect(&dst, src);
     *
     *         // Both src and dst have gotten smaller. If dstClip is provided, confirm it is still
     *         // contained in dst, otherwise cannot optimize the sample area and must use a decal instead
     *         if (dstClip) {
     *             for (int i = 0; i < 4; ++i) {
     *                 if (!dst.contains(dstClip[i].fX, dstClip[i].fY)) {
     *                     // Must resort to using a decal mode restricted to the clipped 'src', and
     *                     // use the original dst rect (filling in src bounds as needed)
     *                     *outSrcRect = src;
     *                     *outDstRect = origDstRect;
     *                     return ImageDrawMode::kDecal;
     *                 }
     *             }
     *         }
     *     }
     *
     *     // The original src and dst were fully contained in the image, or there was no dst clip to
     *     // worry about, or the clip was still contained in the restricted dst rect.
     *     *outSrcRect = src;
     *     *outDstRect = dst;
     *     return ImageDrawMode::kOptimized;
     * }
     * ```
     */
    public fun optimizeSampleArea(
      imageSize: SkISize,
      origSrcRect: SkRect,
      origDstRect: SkRect,
      dstClip: Array<SkPoint>,
      outSrcRect: SkRect?,
      outDstRect: SkRect?,
      outSrcToDst: SkMatrix?,
    ): ImageDrawMode {
      TODO("Implement optimizeSampleArea")
    }

    /**
     * C++ original:
     * ```cpp
     * bool TiledTextureUtils::CanDisableMipmap(const SkMatrix& viewM,
     *                                          const SkMatrix& localM,
     *                                          bool sharpenMipmappedTextures) {
     *     SkMatrix matrix;
     *     matrix.setConcat(viewM, localM);
     *     // With sharp mips, we bias mipmap lookups by -0.5. That means our final LOD is >= 0 until
     *     // the computed LOD is >= 0.5. At what scale factor does a texture get an LOD of
     *     // 0.5?
     *     //
     *     // Want:  0       = log2(1/s) - 0.5
     *     //        0.5     = log2(1/s)
     *     //        2^0.5   = 1/s
     *     //        1/2^0.5 = s
     *     //        2^0.5/2 = s
     *     SkScalar mipScale = sharpenMipmappedTextures ? SK_ScalarRoot2Over2 : SK_Scalar1;
     *     return matrix.getMinScale() >= mipScale;
     * }
     * ```
     */
    public fun canDisableMipmap(
      viewM: SkMatrix,
      localM: SkMatrix,
      sharpenMipmappedTextures: Boolean,
    ): Boolean {
      TODO("Implement canDisableMipmap")
    }

    /**
     * C++ original:
     * ```cpp
     * void TiledTextureUtils::ClampedOutsetWithOffset(SkIRect* iRect, int outset, SkPoint* offset,
     *                                                 const SkIRect& clamp) {
     *     iRect->outset(outset, outset);
     *
     *     int leftClampDelta = clamp.fLeft - iRect->fLeft;
     *     if (leftClampDelta > 0) {
     *         offset->fX -= outset - leftClampDelta;
     *         iRect->fLeft = clamp.fLeft;
     *     } else {
     *         offset->fX -= outset;
     *     }
     *
     *     int topClampDelta = clamp.fTop - iRect->fTop;
     *     if (topClampDelta > 0) {
     *         offset->fY -= outset - topClampDelta;
     *         iRect->fTop = clamp.fTop;
     *     } else {
     *         offset->fY -= outset;
     *     }
     *
     *     if (iRect->fRight > clamp.fRight) {
     *         iRect->fRight = clamp.fRight;
     *     }
     *     if (iRect->fBottom > clamp.fBottom) {
     *         iRect->fBottom = clamp.fBottom;
     *     }
     * }
     * ```
     */
    public fun clampedOutsetWithOffset(
      iRect: SkIRect?,
      outset: Int,
      offset: SkPoint?,
      clamp: SkIRect,
    ) {
      TODO("Implement clampedOutsetWithOffset")
    }

    /**
     * C++ original:
     * ```cpp
     * std::tuple<bool, size_t> TiledTextureUtils::DrawAsTiledImageRect(
     *         SkCanvas* canvas,
     *         const SkImage* image,
     *         const SkRect& srcRect,
     *         const SkRect& dstRect,
     *         SkCanvas::QuadAAFlags aaFlags,
     *         const SkSamplingOptions& origSampling,
     *         const SkPaint* paint,
     *         SkCanvas::SrcRectConstraint constraint,
     *         bool sharpenMM,
     *         size_t cacheSize,
     *         size_t maxTextureSize,
     *         bool renderLazyPictureTilesOnGPU) {
     *     if (canvas->isClipEmpty()) {
     *         return {true, 0};
     *     }
     *
     *     if (!image->isTextureBacked()) {
     *         SkRect src;
     *         SkRect dst;
     *         SkMatrix srcToDst;
     *         ImageDrawMode mode = OptimizeSampleArea(SkISize::Make(image->width(), image->height()),
     *                                                 srcRect, dstRect, /* dstClip= */ nullptr,
     *                                                 &src, &dst, &srcToDst);
     *         if (mode == ImageDrawMode::kSkip) {
     *             return {true, 0};
     *         }
     *
     *         SkASSERT(mode != ImageDrawMode::kDecal); // only happens if there is a 'dstClip'
     *
     *         if (src.contains(image->bounds())) {
     *             constraint = SkCanvas::kFast_SrcRectConstraint;
     *         }
     *
     *         SkDevice* device = SkCanvasPriv::TopDevice(canvas);
     *         const SkMatrix& localToDevice = device->localToDevice();
     *
     *         SkSamplingOptions sampling = origSampling;
     *         if (sampling.mipmap != SkMipmapMode::kNone &&
     *             CanDisableMipmap(localToDevice, srcToDst, sharpenMM)) {
     *             sampling = SkSamplingOptions(sampling.filter);
     *         }
     *
     *         SkIRect clipRect = device->devClipBounds();
     *
     *         int tileFilterPad;
     *         if (sampling.useCubic) {
     *             tileFilterPad = kBicubicFilterTexelPad;
     *         } else if (sampling.filter == SkFilterMode::kLinear || sampling.isAniso()) {
     *             // Aniso will fallback to linear filtering in the tiling case.
     *             tileFilterPad = 1;
     *         } else {
     *             tileFilterPad = 0;
     *         }
     *
     *         int maxTileSize = maxTextureSize - 2 * tileFilterPad;
     *         int tileSize;
     *         SkIRect clippedSubset;
     *         if (ShouldTileImage(clipRect,
     *                             image->dimensions(),
     *                             localToDevice,
     *                             srcToDst,
     *                             &src,
     *                             maxTileSize,
     *                             cacheSize,
     *                             &tileSize,
     *                             &clippedSubset)) {
     *             // If it's a Picture-backed image we should subset the SkPicture directly rather than
     *             // converting to a Bitmap and then subsetting. Rendering to a bitmap will use a Raster
     *             // surface, and the SkPicture could have GPU data.
     *             //
     *             // If we render a subset of a very large SkPicture into a GPU texture "tile", it'll
     *             // require intermediate coordinates that need high precision. If we don't have that,
     *             // bite the bullet and render the lazy picture tiles on the CPU and upload the data
     *             // instead.
     *             if (renderLazyPictureTilesOnGPU &&
     *                 as_IB(image)->type() == SkImage_Base::Type::kLazyPicture) {
     *                 auto imageProc = [&](SkIRect iTileR) {
     *                     return image->makeSubset(nullptr, iTileR, {});
     *                 };
     *
     *                 size_t tiles = draw_tiled_image(canvas,
     *                                                 imageProc,
     *                                                 image->dimensions(),
     *                                                 tileSize,
     *                                                 srcToDst,
     *                                                 src,
     *                                                 clippedSubset,
     *                                                 paint,
     *                                                 aaFlags,
     *                                                 constraint,
     *                                                 sampling);
     *                 return {true, tiles};
     *             }
     *
     *             // Extract pixels on the CPU, since we have to split into separate textures before
     *             // sending to the GPU if tiling.
     *             if (SkBitmap bm; as_IB(image)->getROPixels(nullptr, &bm)) {
     *                 auto imageProc = [&](SkIRect iTileR) {
     *                     // We must subset as a bitmap and then turn it into an SkImage if we want
     *                     // caching to work. Image subsets always make a copy of the pixels and lose
     *                     // the association with the original's SkPixelRef.
     *                     if (SkBitmap subsetBmp; bm.extractSubset(&subsetBmp, iTileR)) {
     *                         return SkMakeImageFromRasterBitmap(subsetBmp, kNever_SkCopyPixelsMode);
     *                     }
     *                     return sk_sp<SkImage>(nullptr);
     *                 };
     *
     *                 size_t tiles = draw_tiled_image(canvas,
     *                                                 imageProc,
     *                                                 bm.dimensions(),
     *                                                 tileSize,
     *                                                 srcToDst,
     *                                                 src,
     *                                                 clippedSubset,
     *                                                 paint,
     *                                                 aaFlags,
     *                                                 constraint,
     *                                                 sampling);
     *                 return {true, tiles};
     *             }
     *         }
     *     }
     *
     *     return {false, 0};
     * }
     * ```
     */
    public fun drawAsTiledImageRect(
      canvas: SkCanvas?,
      image: SkImage?,
      srcRect: SkRect,
      dstRect: SkRect,
      aaFlags: SkCanvas.QuadAAFlags,
      origSampling: SkSamplingOptions,
      paint: SkPaint?,
      constraint: SkCanvas.SrcRectConstraint,
      sharpenMM: Boolean,
      cacheSize: ULong,
      maxTextureSize: ULong,
      renderLazyPictureTilesOnGPU: Boolean = TODO(),
    ): Int {
      TODO("Implement drawAsTiledImageRect")
    }
  }
}
