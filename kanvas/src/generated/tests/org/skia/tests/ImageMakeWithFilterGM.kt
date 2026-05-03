package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageMakeWithFilterGM : public skiagm::GM {
 * public:
 *     ImageMakeWithFilterGM (Strategy strategy, bool filterWithCropRect = false)
 *             : fStrategy(strategy)
 *             , fFilterWithCropRect(filterWithCropRect)
 *             , fMainImage(nullptr)
 *             , fAuxImage(nullptr) {}
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name = SkString("imagemakewithfilter");
 *
 *         if (fFilterWithCropRect) {
 *             name.append("_crop");
 *         }
 *         if (fStrategy == Strategy::kSaveLayer) {
 *             name.append("_ref");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(1840, 860); }
 *
 *     void onOnceBeforeDraw() override {
 *         SkImageInfo info = SkImageInfo::MakeN32(100, 100, kUnpremul_SkAlphaType);
 *         auto surface = SkSurfaces::Raster(info, nullptr);
 *
 *         sk_sp<SkImage> colorImage = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
 *         // Resize to 100x100
 *         surface->getCanvas()->drawImageRect(
 *                 colorImage, SkRect::MakeWH(colorImage->width(), colorImage->height()),
 *                 SkRect::MakeWH(info.width(), info.height()), SkSamplingOptions(), nullptr,
 *                                             SkCanvas::kStrict_SrcRectConstraint);
 *         fMainImage = surface->makeImageSnapshot();
 *
 *         ToolUtils::draw_checkerboard(surface->getCanvas());
 *         fAuxImage = surface->makeImageSnapshot();
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         FilterFactory filters[] = {
 *             color_filter_factory,
 *             blur_filter_factory,
 *             drop_shadow_factory,
 *             offset_factory,
 *             dilate_factory,
 *             erode_factory,
 *             displacement_factory,
 *             arithmetic_factory,
 *             blend_factory,
 *             convolution_factory,
 *             matrix_factory,
 *             lighting_factory,
 *             tile_factory
 *         };
 *         const char* filterNames[] = {
 *             "Color",
 *             "Blur",
 *             "Drop Shadow",
 *             "Offset",
 *             "Dilate",
 *             "Erode",
 *             "Displacement",
 *             "Arithmetic",
 *             "Blend",
 *             "Convolution",
 *             "Matrix Xform",
 *             "Lighting",
 *             "Tile"
 *         };
 *         static_assert(std::size(filters) == std::size(filterNames), "filter name length");
 *
 *         SkIRect clipBounds[] {
 *             { -20, -20, 100, 100 },
 *             {   0,   0,  75,  75 },
 *             {  20,  20, 100, 100 },
 *             { -20, -20,  50,  50 },
 *             {  20,  20,  50,  50 },
 *             {  30,  30,  75,  75 }
 *         };
 *
 * #if defined(SK_GANESH)
 *         auto rContext = canvas->recordingContext();
 *         // In a DDL context, we can't use the GPU code paths and we will drop the work – skip.
 *         auto dContext = GrAsDirectContext(rContext);
 *         if (rContext) {
 *             if (!dContext) {
 *                 *errorMsg = "Requires a direct context.";
 *                 return DrawResult::kSkip;
 *             }
 *             if (dContext->abandoned()) {
 *                 *errorMsg = "Direct context abandoned.";
 *                 return DrawResult::kSkip;
 *             }
 *         }
 * #else
 *         constexpr void* dContext = nullptr;
 *         (void)dContext;
 * #endif
 *
 *         // These need to be GPU-backed when on the GPU to ensure that the image filters use the GPU
 *         // code paths (otherwise they may choose to do CPU filtering then upload)
 *         sk_sp<SkImage> mainImage = ToolUtils::MakeTextureImage(canvas, fMainImage);
 *         sk_sp<SkImage> auxImage = ToolUtils::MakeTextureImage(canvas, fAuxImage);
 *         if (!mainImage || !auxImage) {
 *             return DrawResult::kFail;
 *         }
 *         SkASSERT(mainImage && (mainImage->isTextureBacked() || !dContext));
 *         SkASSERT(auxImage && (auxImage->isTextureBacked() || !dContext));
 *
 *         SkScalar MARGIN = SkIntToScalar(40);
 *         SkScalar DX = mainImage->width() + MARGIN;
 *         SkScalar DY = auxImage->height() + MARGIN;
 *
 *         // Header hinting at what the filters do
 *         SkPaint textPaint;
 *         textPaint.setAntiAlias(true);
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setSize(12);
 *         for (size_t i = 0; i < std::size(filterNames); ++i) {
 *             canvas->drawString(filterNames[i], DX * i + MARGIN, 15, font, textPaint);
 *         }
 *
 *         canvas->translate(MARGIN, MARGIN);
 *
 *         for (auto clipBound : clipBounds) {
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(filters); ++i) {
 *                 SkIRect subset = SkIRect::MakeXYWH(25, 25, 50, 50);
 *                 SkIRect outSubset;
 *
 *                 // Draw the original image faintly so that it aids in checking alignment of the
 *                 // filtered result.
 *                 SkPaint alpha;
 *                 alpha.setAlphaf(0.3f);
 *                 canvas->drawImage(mainImage, 0, 0, SkSamplingOptions(), &alpha);
 *
 *                 this->drawImageWithFilter(canvas, mainImage, auxImage, filters[i], clipBound,
 *                                           subset, &outSubset);
 *
 *                 // Draw outlines to highlight what was subset, what was cropped, and what was output
 *                 // (no output subset is displayed for kSaveLayer since that information isn't avail)
 *                 SkIRect* outSubsetBounds = nullptr;
 *                 if (fStrategy != Strategy::kSaveLayer) {
 *                     outSubsetBounds = &outSubset;
 *                 }
 *                 show_bounds(canvas, &clipBound, &subset, outSubsetBounds);
 *
 *                 canvas->translate(DX, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, DY);
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     Strategy fStrategy;
 *     bool fFilterWithCropRect;
 *     sk_sp<SkImage> fMainImage;
 *     sk_sp<SkImage> fAuxImage;
 *
 *     void drawImageWithFilter(SkCanvas* canvas, sk_sp<SkImage> mainImage, sk_sp<SkImage> auxImage,
 *                              FilterFactory filterFactory, const SkIRect& clip,
 *                              const SkIRect& subset, SkIRect* dstRect) {
 *         // When creating the filter with a crop rect equal to the clip, we should expect to see no
 *         // difference from a filter without a crop rect. However, if the CTM isn't managed properly
 *         // by MakeWithFilter, then the final result will be the incorrect intersection of the clip
 *         // and the transformed crop rect.
 *         sk_sp<SkImageFilter> filter = filterFactory(auxImage,
 *                                                     fFilterWithCropRect ? &clip : nullptr);
 *
 *         if (fStrategy == Strategy::kSaveLayer) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *
 *             // Clip before the saveLayer with the filter
 *             canvas->clipRect(SkRect::Make(clip));
 *
 *             // Put the image filter on the layer
 *             SkPaint paint;
 *             paint.setImageFilter(filter);
 *             canvas->saveLayer(nullptr, &paint);
 *
 *             // Draw the original subset of the image
 *             SkRect r = SkRect::Make(subset);
 *             canvas->drawImageRect(mainImage, r, r, SkSamplingOptions(),
 *                                   nullptr, SkCanvas::kStrict_SrcRectConstraint);
 *
 *             *dstRect = subset;
 *         } else {
 *             sk_sp<SkImage> result;
 *             SkIRect outSubset;
 *             SkIPoint offset;
 *
 * #if defined(SK_GANESH)
 *             if (auto rContext = canvas->recordingContext()) {
 *                 result = SkImages::MakeWithFilter(rContext, mainImage, filter.get(),
 *                                                   subset, clip, &outSubset, &offset);
 *             } else
 * #endif
 * #if defined(SK_GRAPHITE)
 *             if (auto recorder = canvas->recorder()){
 *                 result = SkImages::MakeWithFilter(recorder, mainImage, filter.get(),
 *                                                   subset, clip, &outSubset, &offset);
 *             } else
 * #endif
 *             {
 *                 result = SkImages::MakeWithFilter(mainImage, filter.get(),
 *                                                   subset, clip, &outSubset, &offset);
 *             }
 *
 *             if (!result) {
 *                 return;
 *             }
 *
 *             SkASSERT(mainImage->isTextureBacked() == result->isTextureBacked());
 *
 *             *dstRect = SkIRect::MakeXYWH(offset.x(), offset.y(),
 *                                          outSubset.width(), outSubset.height());
 *             canvas->drawImageRect(result, SkRect::Make(outSubset), SkRect::Make(*dstRect),
 *                                   SkSamplingOptions(), nullptr,
 *                                   SkCanvas::kStrict_SrcRectConstraint);
 *         }
 *     }
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageMakeWithFilterGM public constructor(
  strategy: Strategy,
  filterWithCropRect: Boolean = TODO(),
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * Strategy fStrategy
   * ```
   */
  private var fStrategy: Strategy = TODO("Initialize fStrategy")

  /**
   * C++ original:
   * ```cpp
   * bool fFilterWithCropRect
   * ```
   */
  private var fFilterWithCropRect: Boolean = TODO("Initialize fFilterWithCropRect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fMainImage
   * ```
   */
  private var fMainImage: SkSp<SkImage> = TODO("Initialize fMainImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fAuxImage
   * ```
   */
  private var fAuxImage: SkSp<SkImage> = TODO("Initialize fAuxImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name = SkString("imagemakewithfilter");
   *
   *         if (fFilterWithCropRect) {
   *             name.append("_crop");
   *         }
   *         if (fStrategy == Strategy::kSaveLayer) {
   *             name.append("_ref");
   *         }
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1840, 860); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkImageInfo info = SkImageInfo::MakeN32(100, 100, kUnpremul_SkAlphaType);
   *         auto surface = SkSurfaces::Raster(info, nullptr);
   *
   *         sk_sp<SkImage> colorImage = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
   *         // Resize to 100x100
   *         surface->getCanvas()->drawImageRect(
   *                 colorImage, SkRect::MakeWH(colorImage->width(), colorImage->height()),
   *                 SkRect::MakeWH(info.width(), info.height()), SkSamplingOptions(), nullptr,
   *                                             SkCanvas::kStrict_SrcRectConstraint);
   *         fMainImage = surface->makeImageSnapshot();
   *
   *         ToolUtils::draw_checkerboard(surface->getCanvas());
   *         fAuxImage = surface->makeImageSnapshot();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         FilterFactory filters[] = {
   *             color_filter_factory,
   *             blur_filter_factory,
   *             drop_shadow_factory,
   *             offset_factory,
   *             dilate_factory,
   *             erode_factory,
   *             displacement_factory,
   *             arithmetic_factory,
   *             blend_factory,
   *             convolution_factory,
   *             matrix_factory,
   *             lighting_factory,
   *             tile_factory
   *         };
   *         const char* filterNames[] = {
   *             "Color",
   *             "Blur",
   *             "Drop Shadow",
   *             "Offset",
   *             "Dilate",
   *             "Erode",
   *             "Displacement",
   *             "Arithmetic",
   *             "Blend",
   *             "Convolution",
   *             "Matrix Xform",
   *             "Lighting",
   *             "Tile"
   *         };
   *         static_assert(std::size(filters) == std::size(filterNames), "filter name length");
   *
   *         SkIRect clipBounds[] {
   *             { -20, -20, 100, 100 },
   *             {   0,   0,  75,  75 },
   *             {  20,  20, 100, 100 },
   *             { -20, -20,  50,  50 },
   *             {  20,  20,  50,  50 },
   *             {  30,  30,  75,  75 }
   *         };
   *
   * #if defined(SK_GANESH)
   *         auto rContext = canvas->recordingContext();
   *         // In a DDL context, we can't use the GPU code paths and we will drop the work – skip.
   *         auto dContext = GrAsDirectContext(rContext);
   *         if (rContext) {
   *             if (!dContext) {
   *                 *errorMsg = "Requires a direct context.";
   *                 return DrawResult::kSkip;
   *             }
   *             if (dContext->abandoned()) {
   *                 *errorMsg = "Direct context abandoned.";
   *                 return DrawResult::kSkip;
   *             }
   *         }
   * #else
   *         constexpr void* dContext = nullptr;
   *         (void)dContext;
   * #endif
   *
   *         // These need to be GPU-backed when on the GPU to ensure that the image filters use the GPU
   *         // code paths (otherwise they may choose to do CPU filtering then upload)
   *         sk_sp<SkImage> mainImage = ToolUtils::MakeTextureImage(canvas, fMainImage);
   *         sk_sp<SkImage> auxImage = ToolUtils::MakeTextureImage(canvas, fAuxImage);
   *         if (!mainImage || !auxImage) {
   *             return DrawResult::kFail;
   *         }
   *         SkASSERT(mainImage && (mainImage->isTextureBacked() || !dContext));
   *         SkASSERT(auxImage && (auxImage->isTextureBacked() || !dContext));
   *
   *         SkScalar MARGIN = SkIntToScalar(40);
   *         SkScalar DX = mainImage->width() + MARGIN;
   *         SkScalar DY = auxImage->height() + MARGIN;
   *
   *         // Header hinting at what the filters do
   *         SkPaint textPaint;
   *         textPaint.setAntiAlias(true);
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setSize(12);
   *         for (size_t i = 0; i < std::size(filterNames); ++i) {
   *             canvas->drawString(filterNames[i], DX * i + MARGIN, 15, font, textPaint);
   *         }
   *
   *         canvas->translate(MARGIN, MARGIN);
   *
   *         for (auto clipBound : clipBounds) {
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(filters); ++i) {
   *                 SkIRect subset = SkIRect::MakeXYWH(25, 25, 50, 50);
   *                 SkIRect outSubset;
   *
   *                 // Draw the original image faintly so that it aids in checking alignment of the
   *                 // filtered result.
   *                 SkPaint alpha;
   *                 alpha.setAlphaf(0.3f);
   *                 canvas->drawImage(mainImage, 0, 0, SkSamplingOptions(), &alpha);
   *
   *                 this->drawImageWithFilter(canvas, mainImage, auxImage, filters[i], clipBound,
   *                                           subset, &outSubset);
   *
   *                 // Draw outlines to highlight what was subset, what was cropped, and what was output
   *                 // (no output subset is displayed for kSaveLayer since that information isn't avail)
   *                 SkIRect* outSubsetBounds = nullptr;
   *                 if (fStrategy != Strategy::kSaveLayer) {
   *                     outSubsetBounds = &outSubset;
   *                 }
   *                 show_bounds(canvas, &clipBound, &subset, outSubsetBounds);
   *
   *                 canvas->translate(DX, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, DY);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageWithFilter(SkCanvas* canvas, sk_sp<SkImage> mainImage, sk_sp<SkImage> auxImage,
   *                              FilterFactory filterFactory, const SkIRect& clip,
   *                              const SkIRect& subset, SkIRect* dstRect) {
   *         // When creating the filter with a crop rect equal to the clip, we should expect to see no
   *         // difference from a filter without a crop rect. However, if the CTM isn't managed properly
   *         // by MakeWithFilter, then the final result will be the incorrect intersection of the clip
   *         // and the transformed crop rect.
   *         sk_sp<SkImageFilter> filter = filterFactory(auxImage,
   *                                                     fFilterWithCropRect ? &clip : nullptr);
   *
   *         if (fStrategy == Strategy::kSaveLayer) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *
   *             // Clip before the saveLayer with the filter
   *             canvas->clipRect(SkRect::Make(clip));
   *
   *             // Put the image filter on the layer
   *             SkPaint paint;
   *             paint.setImageFilter(filter);
   *             canvas->saveLayer(nullptr, &paint);
   *
   *             // Draw the original subset of the image
   *             SkRect r = SkRect::Make(subset);
   *             canvas->drawImageRect(mainImage, r, r, SkSamplingOptions(),
   *                                   nullptr, SkCanvas::kStrict_SrcRectConstraint);
   *
   *             *dstRect = subset;
   *         } else {
   *             sk_sp<SkImage> result;
   *             SkIRect outSubset;
   *             SkIPoint offset;
   *
   * #if defined(SK_GANESH)
   *             if (auto rContext = canvas->recordingContext()) {
   *                 result = SkImages::MakeWithFilter(rContext, mainImage, filter.get(),
   *                                                   subset, clip, &outSubset, &offset);
   *             } else
   * #endif
   * #if defined(SK_GRAPHITE)
   *             if (auto recorder = canvas->recorder()){
   *                 result = SkImages::MakeWithFilter(recorder, mainImage, filter.get(),
   *                                                   subset, clip, &outSubset, &offset);
   *             } else
   * #endif
   *             {
   *                 result = SkImages::MakeWithFilter(mainImage, filter.get(),
   *                                                   subset, clip, &outSubset, &offset);
   *             }
   *
   *             if (!result) {
   *                 return;
   *             }
   *
   *             SkASSERT(mainImage->isTextureBacked() == result->isTextureBacked());
   *
   *             *dstRect = SkIRect::MakeXYWH(offset.x(), offset.y(),
   *                                          outSubset.width(), outSubset.height());
   *             canvas->drawImageRect(result, SkRect::Make(outSubset), SkRect::Make(*dstRect),
   *                                   SkSamplingOptions(), nullptr,
   *                                   SkCanvas::kStrict_SrcRectConstraint);
   *         }
   *     }
   * ```
   */
  private fun drawImageWithFilter(
    canvas: SkCanvas?,
    mainImage: SkSp<SkImage>,
    auxImage: SkSp<SkImage>,
    filterFactory: FilterFactory,
    clip: SkIRect,
    subset: SkIRect,
    dstRect: SkIRect?,
  ) {
    TODO("Implement drawImageWithFilter")
  }
}
