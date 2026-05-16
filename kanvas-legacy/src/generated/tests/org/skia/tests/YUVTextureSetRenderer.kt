package org.skia.tests

import kotlin.Array
import kotlin.BooleanArray
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class YUVTextureSetRenderer : public ClipTileRenderer {
 * public:
 *     static sk_sp<ClipTileRenderer> MakeFromJPEG(sk_sp<SkData> imageData) {
 *         return sk_sp<ClipTileRenderer>(new YUVTextureSetRenderer(std::move(imageData)));
 *     }
 *
 *     int drawTiles(SkCanvas* canvas) override {
 *         // Refresh the SkImage at the start, so that it's not attempted for every set entry
 *         if (fYUVData) {
 * #if defined(SK_GANESH)
 *             fImage = fYUVData->refImage(canvas->recordingContext(),
 *                                         sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
 *
 * #endif
 *             if (!fImage) {
 *                 return 0;
 *             }
 *         }
 *
 *         int draws = this->INHERITED::drawTiles(canvas);
 *         // Push the last tile set
 *         draws += this->drawAndReset(canvas);
 *         return draws;
 *     }
 *
 *     int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
 *                   int tileID, int quadID) override {
 *         SkASSERT(fImage);
 *         // Now don't actually draw the tile, accumulate it in the growing entry set
 *         bool hasClip = false;
 *         if (clip) {
 *             // Record the four points into fDstClips
 *             fDstClips.push_back_n(4, clip);
 *             hasClip = true;
 *         }
 *
 *         // This acts like the whole image is rendered over the entire tile grid, so derive local
 *         // coordinates from 'rect', based on the grid to image transform.
 *         SkMatrix gridToImage = SkMatrix::RectToRectOrIdentity(
 *                                 SkRect::MakeWH(kColCount * kTileWidth, kRowCount * kTileHeight),
 *                                 SkRect::MakeWH(fImage->width(), fImage->height()));
 *         SkRect localRect = gridToImage.mapRect(rect);
 *
 *         // drawTextureSet automatically derives appropriate local quad from localRect if clipPtr
 *         // is not null. Also exercise per-entry alpha combined with YUVA images.
 *         fSetEntries.push_back(
 *                 {fImage, localRect, rect, -1, .5f, this->maskToFlags(edgeAA), hasClip});
 *         return 0;
 *     }
 *
 *     void drawBanner(SkCanvas* canvas) override {
 *         draw_text(canvas, "Texture");
 *         canvas->translate(0.f, 15.f);
 *         draw_text(canvas, "YUV + alpha - GPU Only");
 *     }
 *
 * private:
 *     std::unique_ptr<sk_gpu_test::LazyYUVImage> fYUVData;
 *     // The last accessed SkImage from fYUVData, held here for easy access by drawTile
 *     sk_sp<SkImage> fImage;
 *
 *     TArray<SkPoint> fDstClips;
 *     TArray<SkCanvas::ImageSetEntry> fSetEntries;
 *
 *     YUVTextureSetRenderer(sk_sp<SkData> jpegData)
 *             : fYUVData(sk_gpu_test::LazyYUVImage::Make(std::move(jpegData)))
 *             , fImage(nullptr) {}
 *
 *     int drawAndReset(SkCanvas* canvas) {
 *         // Early out if there's nothing to draw
 *         if (fSetEntries.size() == 0) {
 *             SkASSERT(fDstClips.size() == 0);
 *             return 0;
 *         }
 *
 * #ifdef SK_DEBUG
 *         int expectedDstClipCount = 0;
 *         for (int i = 0; i < fSetEntries.size(); ++i) {
 *             expectedDstClipCount += 4 * fSetEntries[i].fHasClip;
 *         }
 *         SkASSERT(expectedDstClipCount == fDstClips.size());
 * #endif
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *
 *         canvas->experimental_DrawEdgeAAImageSet(
 *                 fSetEntries.begin(), fSetEntries.size(), fDstClips.begin(), nullptr,
 *                 SkSamplingOptions(SkFilterMode::kLinear), &paint,
 *                 SkCanvas::kFast_SrcRectConstraint);
 *
 *         // Reset for next tile
 *         fDstClips.clear();
 *         fSetEntries.clear();
 *
 *         return 1;
 *     }
 *
 *     using INHERITED = ClipTileRenderer;
 * }
 * ```
 */
public open class YUVTextureSetRenderer public constructor(
  jpegData: SkSp<SkData>,
) : ClipTileRenderer() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<sk_gpu_test::LazyYUVImage> fYUVData
   * ```
   */
  private var fYUVData: Int = TODO("Initialize fYUVData")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkPoint> fDstClips
   * ```
   */
  private var fDstClips: Int = TODO("Initialize fDstClips")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkCanvas::ImageSetEntry> fSetEntries
   * ```
   */
  private var fSetEntries: Int = TODO("Initialize fSetEntries")

  /**
   * C++ original:
   * ```cpp
   * int drawTiles(SkCanvas* canvas) override {
   *         // Refresh the SkImage at the start, so that it's not attempted for every set entry
   *         if (fYUVData) {
   * #if defined(SK_GANESH)
   *             fImage = fYUVData->refImage(canvas->recordingContext(),
   *                                         sk_gpu_test::LazyYUVImage::Type::kFromPixmaps);
   *
   * #endif
   *             if (!fImage) {
   *                 return 0;
   *             }
   *         }
   *
   *         int draws = this->INHERITED::drawTiles(canvas);
   *         // Push the last tile set
   *         draws += this->drawAndReset(canvas);
   *         return draws;
   *     }
   * ```
   */
  public override fun drawTiles(canvas: SkCanvas?): Int {
    TODO("Implement drawTiles")
  }

  /**
   * C++ original:
   * ```cpp
   * int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
   *                   int tileID, int quadID) override {
   *         SkASSERT(fImage);
   *         // Now don't actually draw the tile, accumulate it in the growing entry set
   *         bool hasClip = false;
   *         if (clip) {
   *             // Record the four points into fDstClips
   *             fDstClips.push_back_n(4, clip);
   *             hasClip = true;
   *         }
   *
   *         // This acts like the whole image is rendered over the entire tile grid, so derive local
   *         // coordinates from 'rect', based on the grid to image transform.
   *         SkMatrix gridToImage = SkMatrix::RectToRectOrIdentity(
   *                                 SkRect::MakeWH(kColCount * kTileWidth, kRowCount * kTileHeight),
   *                                 SkRect::MakeWH(fImage->width(), fImage->height()));
   *         SkRect localRect = gridToImage.mapRect(rect);
   *
   *         // drawTextureSet automatically derives appropriate local quad from localRect if clipPtr
   *         // is not null. Also exercise per-entry alpha combined with YUVA images.
   *         fSetEntries.push_back(
   *                 {fImage, localRect, rect, -1, .5f, this->maskToFlags(edgeAA), hasClip});
   *         return 0;
   *     }
   * ```
   */
  public override fun drawTile(
    canvas: SkCanvas?,
    rect: SkRect,
    clip: Array<SkPoint>,
    edgeAA: BooleanArray,
    tileID: Int,
    quadID: Int,
  ): Int {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawBanner(SkCanvas* canvas) override {
   *         draw_text(canvas, "Texture");
   *         canvas->translate(0.f, 15.f);
   *         draw_text(canvas, "YUV + alpha - GPU Only");
   *     }
   * ```
   */
  public override fun drawBanner(canvas: SkCanvas?) {
    TODO("Implement drawBanner")
  }

  /**
   * C++ original:
   * ```cpp
   * int drawAndReset(SkCanvas* canvas) {
   *         // Early out if there's nothing to draw
   *         if (fSetEntries.size() == 0) {
   *             SkASSERT(fDstClips.size() == 0);
   *             return 0;
   *         }
   *
   * #ifdef SK_DEBUG
   *         int expectedDstClipCount = 0;
   *         for (int i = 0; i < fSetEntries.size(); ++i) {
   *             expectedDstClipCount += 4 * fSetEntries[i].fHasClip;
   *         }
   *         SkASSERT(expectedDstClipCount == fDstClips.size());
   * #endif
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setBlendMode(SkBlendMode::kSrcOver);
   *
   *         canvas->experimental_DrawEdgeAAImageSet(
   *                 fSetEntries.begin(), fSetEntries.size(), fDstClips.begin(), nullptr,
   *                 SkSamplingOptions(SkFilterMode::kLinear), &paint,
   *                 SkCanvas::kFast_SrcRectConstraint);
   *
   *         // Reset for next tile
   *         fDstClips.clear();
   *         fSetEntries.clear();
   *
   *         return 1;
   *     }
   * ```
   */
  private fun drawAndReset(canvas: SkCanvas?): Int {
    TODO("Implement drawAndReset")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeFromJPEG(sk_sp<SkData> imageData) {
     *         return sk_sp<ClipTileRenderer>(new YUVTextureSetRenderer(std::move(imageData)));
     *     }
     * ```
     */
    public fun makeFromJPEG(imageData: SkSp<SkData>): SkSp<ClipTileRenderer> {
      TODO("Implement makeFromJPEG")
    }
  }
}
