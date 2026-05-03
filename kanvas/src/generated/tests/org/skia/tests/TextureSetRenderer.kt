package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class TextureSetRenderer : public ClipTileRenderer {
 * public:
 *
 *     static sk_sp<ClipTileRenderer> MakeUnbatched(sk_sp<SkImage> image) {
 *         return Make("Texture", "", std::move(image), nullptr, nullptr, nullptr, nullptr,
 *                     1.f, true, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeBatched(sk_sp<SkImage> image, int transformCount) {
 *         const char* subtitle = transformCount == 0 ? "" : "w/ xforms";
 *         return Make("Texture Set", subtitle, std::move(image), nullptr, nullptr, nullptr, nullptr,
 *                     1.f, false, transformCount);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeShader(const char* name, sk_sp<SkImage> image,
 *                                               sk_sp<SkShader> shader, bool local) {
 *         return Make("Shader", name, std::move(image), std::move(shader),
 *                     nullptr, nullptr, nullptr, 1.f, local, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeColorFilter(const char* name, sk_sp<SkImage> image,
 *                                                    sk_sp<SkColorFilter> filter) {
 *         return Make("Color Filter", name, std::move(image), nullptr, std::move(filter), nullptr,
 *                     nullptr, 1.f, false, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeImageFilter(const char* name, sk_sp<SkImage> image,
 *                                                    sk_sp<SkImageFilter> filter) {
 *         return Make("Image Filter", name, std::move(image), nullptr, nullptr, std::move(filter),
 *                     nullptr, 1.f, false, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeMaskFilter(const char* name, sk_sp<SkImage> image,
 *                                                   sk_sp<SkMaskFilter> filter) {
 *         return Make("Mask Filter", name, std::move(image), nullptr, nullptr, nullptr,
 *                     std::move(filter), 1.f, false, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeAlpha(sk_sp<SkImage> image, SkScalar alpha) {
 *         return Make("Alpha", SkStringPrintf("a = %.2f", alpha).c_str(), std::move(image), nullptr,
 *                     nullptr, nullptr, nullptr, alpha, false, 0);
 *     }
 *
 *     static sk_sp<ClipTileRenderer> Make(const char* topBanner, const char* bottomBanner,
 *                                         sk_sp<SkImage> image, sk_sp<SkShader> shader,
 *                                         sk_sp<SkColorFilter> colorFilter,
 *                                         sk_sp<SkImageFilter> imageFilter,
 *                                         sk_sp<SkMaskFilter> maskFilter, SkScalar paintAlpha,
 *                                         bool resetAfterEachQuad, int transformCount) {
 *         return sk_sp<ClipTileRenderer>(new TextureSetRenderer(topBanner, bottomBanner,
 *                 std::move(image), std::move(shader), std::move(colorFilter), std::move(imageFilter),
 *                 std::move(maskFilter), paintAlpha, resetAfterEachQuad, transformCount));
 *     }
 *
 *     int drawTiles(SkCanvas* canvas) override {
 *         int draws = this->INHERITED::drawTiles(canvas);
 *         // Push the last tile set
 *         draws += this->drawAndReset(canvas);
 *         return draws;
 *     }
 *
 *     int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
 *                   int tileID, int quadID) override {
 *         // Now don't actually draw the tile, accumulate it in the growing entry set
 *         bool hasClip = false;
 *         if (clip) {
 *             // Record the four points into fDstClips
 *             fDstClips.push_back_n(4, clip);
 *             hasClip = true;
 *         }
 *
 *         int matrixIdx = -1;
 *         if (!fResetEachQuad && fTransformBatchCount > 0) {
 *             // Handle transform batching. This works by capturing the CTM of the first tile draw,
 *             // and then calculate the difference between that and future CTMs for later tiles.
 *             if (fPreViewMatrices.size() == 0) {
 *                 fBaseCTM = canvas->getTotalMatrix();
 *                 fPreViewMatrices.push_back(SkMatrix::I());
 *                 matrixIdx = 0;
 *             } else {
 *                 // Calculate matrix s.t. getTotalMatrix() = fBaseCTM * M
 *                 SkMatrix invBase;
 *                 if (!fBaseCTM.invert(&invBase)) {
 *                     SkDebugf("Cannot invert CTM, transform batching will not be correct.\n");
 *                 } else {
 *                     SkMatrix preView = SkMatrix::Concat(invBase, canvas->getTotalMatrix());
 *                     if (preView != fPreViewMatrices[fPreViewMatrices.size() - 1]) {
 *                         // Add the new matrix
 *                         fPreViewMatrices.push_back(preView);
 *                     } // else re-use the last matrix
 *                     matrixIdx = fPreViewMatrices.size() - 1;
 *                 }
 *             }
 *         }
 *
 *         // This acts like the whole image is rendered over the entire tile grid, so derive local
 *         // coordinates from 'rect', based on the grid to image transform.
 *         SkMatrix gridToImage = SkMatrix::RectToRectOrIdentity(
 *                                     SkRect::MakeWH(kColCount * kTileWidth, kRowCount * kTileHeight),
 *                                     SkRect::MakeWH(fImage->width(), fImage->height()));
 *         SkRect localRect = gridToImage.mapRect(rect);
 *
 *         // drawTextureSet automatically derives appropriate local quad from localRect if clipPtr
 *         // is not null.
 *         fSetEntries.push_back(
 *                 {fImage, localRect, rect, matrixIdx, 1.f, this->maskToFlags(edgeAA), hasClip});
 *
 *         if (fResetEachQuad) {
 *             // Only ever draw one entry at a time
 *             return this->drawAndReset(canvas);
 *         } else {
 *             return 0;
 *         }
 *     }
 *
 *     void drawBanner(SkCanvas* canvas) override {
 *         if (fTopBanner.size() > 0) {
 *             draw_text(canvas, fTopBanner.c_str());
 *         }
 *         canvas->translate(0.f, 15.f);
 *         if (fBottomBanner.size() > 0) {
 *             draw_text(canvas, fBottomBanner.c_str());
 *         }
 *     }
 *
 * private:
 *     SkString fTopBanner;
 *     SkString fBottomBanner;
 *
 *     sk_sp<SkImage> fImage;
 *     sk_sp<SkShader> fShader;
 *     sk_sp<SkColorFilter> fColorFilter;
 *     sk_sp<SkImageFilter> fImageFilter;
 *     sk_sp<SkMaskFilter> fMaskFilter;
 *     SkScalar fPaintAlpha;
 *
 *     // Batching rules
 *     bool fResetEachQuad;
 *     int fTransformBatchCount;
 *
 *     TArray<SkPoint> fDstClips;
 *     TArray<SkMatrix> fPreViewMatrices;
 *     TArray<SkCanvas::ImageSetEntry> fSetEntries;
 *
 *     SkMatrix fBaseCTM;
 *     int fBatchCount;
 *
 *     TextureSetRenderer(const char* topBanner,
 *                        const char* bottomBanner,
 *                        sk_sp<SkImage> image,
 *                        sk_sp<SkShader> shader,
 *                        sk_sp<SkColorFilter> colorFilter,
 *                        sk_sp<SkImageFilter> imageFilter,
 *                        sk_sp<SkMaskFilter> maskFilter,
 *                        SkScalar paintAlpha,
 *                        bool resetEachQuad,
 *                        int transformBatchCount)
 *             : fTopBanner(topBanner)
 *             , fBottomBanner(bottomBanner)
 *             , fImage(std::move(image))
 *             , fShader(std::move(shader))
 *             , fColorFilter(std::move(colorFilter))
 *             , fImageFilter(std::move(imageFilter))
 *             , fMaskFilter(std::move(maskFilter))
 *             , fPaintAlpha(paintAlpha)
 *             , fResetEachQuad(resetEachQuad)
 *             , fTransformBatchCount(transformBatchCount)
 *             , fBatchCount(0) {
 *         SkASSERT(transformBatchCount >= 0 && (!resetEachQuad || transformBatchCount == 0));
 *     }
 *
 *     void configureTilePaint(const SkRect& rect, SkPaint* paint) const {
 *         paint->setAntiAlias(true);
 *         paint->setBlendMode(SkBlendMode::kSrcOver);
 *
 *         // Send non-white RGB, that should be ignored
 *         paint->setColor4f({1.f, 0.4f, 0.25f, fPaintAlpha}, nullptr);
 *
 *
 *         if (fShader) {
 *             if (fResetEachQuad) {
 *                 // Apply a local transform in the shader to map from the tile rectangle to (0,0,w,h)
 *                 static const SkRect kTarget = SkRect::MakeWH(kTileWidth, kTileHeight);
 *                 SkMatrix local = SkMatrix::RectToRectOrIdentity(kTarget, rect);
 *                 paint->setShader(fShader->makeWithLocalMatrix(local));
 *             } else {
 *                 paint->setShader(fShader);
 *             }
 *         }
 *
 *         paint->setColorFilter(fColorFilter);
 *         paint->setImageFilter(fImageFilter);
 *         paint->setMaskFilter(fMaskFilter);
 *     }
 *
 *     int drawAndReset(SkCanvas* canvas) {
 *         // Early out if there's nothing to draw
 *         if (fSetEntries.size() == 0) {
 *             SkASSERT(fDstClips.size() == 0 && fPreViewMatrices.size() == 0);
 *             return 0;
 *         }
 *
 *         if (!fResetEachQuad && fTransformBatchCount > 0) {
 *             // A batch is completed
 *             fBatchCount++;
 *             if (fBatchCount < fTransformBatchCount) {
 *                 // Haven't hit the point to submit yet, but end the current tile
 *                 return 0;
 *             }
 *
 *             // Submitting all tiles back to where fBaseCTM was the canvas' matrix, while the
 *             // canvas currently has the CTM of the last tile batch, so reset it.
 *             canvas->setMatrix(fBaseCTM);
 *         }
 *
 * #ifdef SK_DEBUG
 *         int expectedDstClipCount = 0;
 *         for (int i = 0; i < fSetEntries.size(); ++i) {
 *             expectedDstClipCount += 4 * fSetEntries[i].fHasClip;
 *             SkASSERT(fSetEntries[i].fMatrixIndex < 0 ||
 *                      fSetEntries[i].fMatrixIndex < fPreViewMatrices.size());
 *         }
 *         SkASSERT(expectedDstClipCount == fDstClips.size());
 * #endif
 *
 *         SkPaint paint;
 *         SkRect lastTileRect = fSetEntries[fSetEntries.size() - 1].fDstRect;
 *         this->configureTilePaint(lastTileRect, &paint);
 *
 *         canvas->experimental_DrawEdgeAAImageSet(
 *                 fSetEntries.begin(), fSetEntries.size(), fDstClips.begin(),
 *                 fPreViewMatrices.begin(), SkSamplingOptions(SkFilterMode::kLinear),
 *                 &paint, SkCanvas::kFast_SrcRectConstraint);
 *
 *         // Reset for next tile
 *         fDstClips.clear();
 *         fPreViewMatrices.clear();
 *         fSetEntries.clear();
 *         fBatchCount = 0;
 *
 *         return 1;
 *     }
 *
 *     using INHERITED = ClipTileRenderer;
 * }
 * ```
 */
public open class TextureSetRenderer public constructor(
  topBanner: String?,
  bottomBanner: String?,
  image: SkSp<SkImage>,
  shader: SkSp<SkShader>,
  colorFilter: SkSp<SkColorFilter>,
  imageFilter: SkSp<SkImageFilter>,
  maskFilter: SkSp<SkMaskFilter>,
  paintAlpha: SkScalar,
  resetEachQuad: Boolean,
  transformBatchCount: Int,
) : ClipTileRenderer() {
  /**
   * C++ original:
   * ```cpp
   * SkString fTopBanner
   * ```
   */
  private var fTopBanner: String = TODO("Initialize fTopBanner")

  /**
   * C++ original:
   * ```cpp
   * SkString fBottomBanner
   * ```
   */
  private var fBottomBanner: String = TODO("Initialize fBottomBanner")

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
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fColorFilter
   * ```
   */
  private var fColorFilter: SkSp<SkColorFilter> = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> fImageFilter
   * ```
   */
  private var fImageFilter: SkSp<SkImageFilter> = TODO("Initialize fImageFilter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> fMaskFilter
   * ```
   */
  private var fMaskFilter: SkSp<SkMaskFilter> = TODO("Initialize fMaskFilter")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fPaintAlpha
   * ```
   */
  private var fPaintAlpha: SkScalar = TODO("Initialize fPaintAlpha")

  /**
   * C++ original:
   * ```cpp
   * bool fResetEachQuad
   * ```
   */
  private var fResetEachQuad: Boolean = TODO("Initialize fResetEachQuad")

  /**
   * C++ original:
   * ```cpp
   * int fTransformBatchCount
   * ```
   */
  private var fTransformBatchCount: Int = TODO("Initialize fTransformBatchCount")

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
   * TArray<SkMatrix> fPreViewMatrices
   * ```
   */
  private var fPreViewMatrices: Int = TODO("Initialize fPreViewMatrices")

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
   * SkMatrix fBaseCTM
   * ```
   */
  private var fBaseCTM: SkMatrix = TODO("Initialize fBaseCTM")

  /**
   * C++ original:
   * ```cpp
   * int fBatchCount
   * ```
   */
  private var fBatchCount: Int = TODO("Initialize fBatchCount")

  /**
   * C++ original:
   * ```cpp
   * int drawTiles(SkCanvas* canvas) override {
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
   *         // Now don't actually draw the tile, accumulate it in the growing entry set
   *         bool hasClip = false;
   *         if (clip) {
   *             // Record the four points into fDstClips
   *             fDstClips.push_back_n(4, clip);
   *             hasClip = true;
   *         }
   *
   *         int matrixIdx = -1;
   *         if (!fResetEachQuad && fTransformBatchCount > 0) {
   *             // Handle transform batching. This works by capturing the CTM of the first tile draw,
   *             // and then calculate the difference between that and future CTMs for later tiles.
   *             if (fPreViewMatrices.size() == 0) {
   *                 fBaseCTM = canvas->getTotalMatrix();
   *                 fPreViewMatrices.push_back(SkMatrix::I());
   *                 matrixIdx = 0;
   *             } else {
   *                 // Calculate matrix s.t. getTotalMatrix() = fBaseCTM * M
   *                 SkMatrix invBase;
   *                 if (!fBaseCTM.invert(&invBase)) {
   *                     SkDebugf("Cannot invert CTM, transform batching will not be correct.\n");
   *                 } else {
   *                     SkMatrix preView = SkMatrix::Concat(invBase, canvas->getTotalMatrix());
   *                     if (preView != fPreViewMatrices[fPreViewMatrices.size() - 1]) {
   *                         // Add the new matrix
   *                         fPreViewMatrices.push_back(preView);
   *                     } // else re-use the last matrix
   *                     matrixIdx = fPreViewMatrices.size() - 1;
   *                 }
   *             }
   *         }
   *
   *         // This acts like the whole image is rendered over the entire tile grid, so derive local
   *         // coordinates from 'rect', based on the grid to image transform.
   *         SkMatrix gridToImage = SkMatrix::RectToRectOrIdentity(
   *                                     SkRect::MakeWH(kColCount * kTileWidth, kRowCount * kTileHeight),
   *                                     SkRect::MakeWH(fImage->width(), fImage->height()));
   *         SkRect localRect = gridToImage.mapRect(rect);
   *
   *         // drawTextureSet automatically derives appropriate local quad from localRect if clipPtr
   *         // is not null.
   *         fSetEntries.push_back(
   *                 {fImage, localRect, rect, matrixIdx, 1.f, this->maskToFlags(edgeAA), hasClip});
   *
   *         if (fResetEachQuad) {
   *             // Only ever draw one entry at a time
   *             return this->drawAndReset(canvas);
   *         } else {
   *             return 0;
   *         }
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
   *         if (fTopBanner.size() > 0) {
   *             draw_text(canvas, fTopBanner.c_str());
   *         }
   *         canvas->translate(0.f, 15.f);
   *         if (fBottomBanner.size() > 0) {
   *             draw_text(canvas, fBottomBanner.c_str());
   *         }
   *     }
   * ```
   */
  public override fun drawBanner(canvas: SkCanvas?) {
    TODO("Implement drawBanner")
  }

  /**
   * C++ original:
   * ```cpp
   * void configureTilePaint(const SkRect& rect, SkPaint* paint) const {
   *         paint->setAntiAlias(true);
   *         paint->setBlendMode(SkBlendMode::kSrcOver);
   *
   *         // Send non-white RGB, that should be ignored
   *         paint->setColor4f({1.f, 0.4f, 0.25f, fPaintAlpha}, nullptr);
   *
   *
   *         if (fShader) {
   *             if (fResetEachQuad) {
   *                 // Apply a local transform in the shader to map from the tile rectangle to (0,0,w,h)
   *                 static const SkRect kTarget = SkRect::MakeWH(kTileWidth, kTileHeight);
   *                 SkMatrix local = SkMatrix::RectToRectOrIdentity(kTarget, rect);
   *                 paint->setShader(fShader->makeWithLocalMatrix(local));
   *             } else {
   *                 paint->setShader(fShader);
   *             }
   *         }
   *
   *         paint->setColorFilter(fColorFilter);
   *         paint->setImageFilter(fImageFilter);
   *         paint->setMaskFilter(fMaskFilter);
   *     }
   * ```
   */
  private fun configureTilePaint(rect: SkRect, paint: SkPaint?) {
    TODO("Implement configureTilePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * int drawAndReset(SkCanvas* canvas) {
   *         // Early out if there's nothing to draw
   *         if (fSetEntries.size() == 0) {
   *             SkASSERT(fDstClips.size() == 0 && fPreViewMatrices.size() == 0);
   *             return 0;
   *         }
   *
   *         if (!fResetEachQuad && fTransformBatchCount > 0) {
   *             // A batch is completed
   *             fBatchCount++;
   *             if (fBatchCount < fTransformBatchCount) {
   *                 // Haven't hit the point to submit yet, but end the current tile
   *                 return 0;
   *             }
   *
   *             // Submitting all tiles back to where fBaseCTM was the canvas' matrix, while the
   *             // canvas currently has the CTM of the last tile batch, so reset it.
   *             canvas->setMatrix(fBaseCTM);
   *         }
   *
   * #ifdef SK_DEBUG
   *         int expectedDstClipCount = 0;
   *         for (int i = 0; i < fSetEntries.size(); ++i) {
   *             expectedDstClipCount += 4 * fSetEntries[i].fHasClip;
   *             SkASSERT(fSetEntries[i].fMatrixIndex < 0 ||
   *                      fSetEntries[i].fMatrixIndex < fPreViewMatrices.size());
   *         }
   *         SkASSERT(expectedDstClipCount == fDstClips.size());
   * #endif
   *
   *         SkPaint paint;
   *         SkRect lastTileRect = fSetEntries[fSetEntries.size() - 1].fDstRect;
   *         this->configureTilePaint(lastTileRect, &paint);
   *
   *         canvas->experimental_DrawEdgeAAImageSet(
   *                 fSetEntries.begin(), fSetEntries.size(), fDstClips.begin(),
   *                 fPreViewMatrices.begin(), SkSamplingOptions(SkFilterMode::kLinear),
   *                 &paint, SkCanvas::kFast_SrcRectConstraint);
   *
   *         // Reset for next tile
   *         fDstClips.clear();
   *         fPreViewMatrices.clear();
   *         fSetEntries.clear();
   *         fBatchCount = 0;
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
     * static sk_sp<ClipTileRenderer> MakeUnbatched(sk_sp<SkImage> image) {
     *         return Make("Texture", "", std::move(image), nullptr, nullptr, nullptr, nullptr,
     *                     1.f, true, 0);
     *     }
     * ```
     */
    public fun makeUnbatched(image: SkSp<SkImage>): SkSp<ClipTileRenderer> {
      TODO("Implement makeUnbatched")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeBatched(sk_sp<SkImage> image, int transformCount) {
     *         const char* subtitle = transformCount == 0 ? "" : "w/ xforms";
     *         return Make("Texture Set", subtitle, std::move(image), nullptr, nullptr, nullptr, nullptr,
     *                     1.f, false, transformCount);
     *     }
     * ```
     */
    public fun makeBatched(image: SkSp<SkImage>, transformCount: Int): SkSp<ClipTileRenderer> {
      TODO("Implement makeBatched")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeShader(const char* name, sk_sp<SkImage> image,
     *                                               sk_sp<SkShader> shader, bool local) {
     *         return Make("Shader", name, std::move(image), std::move(shader),
     *                     nullptr, nullptr, nullptr, 1.f, local, 0);
     *     }
     * ```
     */
    public fun makeShader(
      name: String?,
      image: SkSp<SkImage>,
      shader: SkSp<SkShader>,
      local: Boolean,
    ): SkSp<ClipTileRenderer> {
      TODO("Implement makeShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeColorFilter(const char* name, sk_sp<SkImage> image,
     *                                                    sk_sp<SkColorFilter> filter) {
     *         return Make("Color Filter", name, std::move(image), nullptr, std::move(filter), nullptr,
     *                     nullptr, 1.f, false, 0);
     *     }
     * ```
     */
    public fun makeColorFilter(
      name: String?,
      image: SkSp<SkImage>,
      filter: SkSp<SkColorFilter>,
    ): SkSp<ClipTileRenderer> {
      TODO("Implement makeColorFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeImageFilter(const char* name, sk_sp<SkImage> image,
     *                                                    sk_sp<SkImageFilter> filter) {
     *         return Make("Image Filter", name, std::move(image), nullptr, nullptr, std::move(filter),
     *                     nullptr, 1.f, false, 0);
     *     }
     * ```
     */
    public fun makeImageFilter(
      name: String?,
      image: SkSp<SkImage>,
      filter: SkSp<SkImageFilter>,
    ): SkSp<ClipTileRenderer> {
      TODO("Implement makeImageFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeMaskFilter(const char* name, sk_sp<SkImage> image,
     *                                                   sk_sp<SkMaskFilter> filter) {
     *         return Make("Mask Filter", name, std::move(image), nullptr, nullptr, nullptr,
     *                     std::move(filter), 1.f, false, 0);
     *     }
     * ```
     */
    public fun makeMaskFilter(
      name: String?,
      image: SkSp<SkImage>,
      filter: SkSp<SkMaskFilter>,
    ): SkSp<ClipTileRenderer> {
      TODO("Implement makeMaskFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeAlpha(sk_sp<SkImage> image, SkScalar alpha) {
     *         return Make("Alpha", SkStringPrintf("a = %.2f", alpha).c_str(), std::move(image), nullptr,
     *                     nullptr, nullptr, nullptr, alpha, false, 0);
     *     }
     * ```
     */
    public fun makeAlpha(image: SkSp<SkImage>, alpha: SkScalar): SkSp<ClipTileRenderer> {
      TODO("Implement makeAlpha")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> Make(const char* topBanner, const char* bottomBanner,
     *                                         sk_sp<SkImage> image, sk_sp<SkShader> shader,
     *                                         sk_sp<SkColorFilter> colorFilter,
     *                                         sk_sp<SkImageFilter> imageFilter,
     *                                         sk_sp<SkMaskFilter> maskFilter, SkScalar paintAlpha,
     *                                         bool resetAfterEachQuad, int transformCount) {
     *         return sk_sp<ClipTileRenderer>(new TextureSetRenderer(topBanner, bottomBanner,
     *                 std::move(image), std::move(shader), std::move(colorFilter), std::move(imageFilter),
     *                 std::move(maskFilter), paintAlpha, resetAfterEachQuad, transformCount));
     *     }
     * ```
     */
    public fun make(
      topBanner: String?,
      bottomBanner: String?,
      image: SkSp<SkImage>,
      shader: SkSp<SkShader>,
      colorFilter: SkSp<SkColorFilter>,
      imageFilter: SkSp<SkImageFilter>,
      maskFilter: SkSp<SkMaskFilter>,
      paintAlpha: SkScalar,
      resetAfterEachQuad: Boolean,
      transformCount: Int,
    ): SkSp<ClipTileRenderer> {
      TODO("Implement make")
    }
  }
}
