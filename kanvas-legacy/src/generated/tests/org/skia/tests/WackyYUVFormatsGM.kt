package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class WackyYUVFormatsGM : public GM {
 * public:
 *     using Type = sk_gpu_test::LazyYUVImage::Type;
 *
 *     WackyYUVFormatsGM(bool useLimitedRange, bool useTargetColorSpace, bool useSubset,
 *                       bool useCubicSampling, Type type)
 *             : fUseLimitedRange(useLimitedRange)
 *             , fUseTargetColorSpace(useTargetColorSpace)
 *             , fUseSubset(useSubset)
 *             , fUseCubicSampling(useCubicSampling)
 *             , fImageType(type) {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("wacky_yuv_formats");
 *         if (fUseLimitedRange) {
 *             name += "_limited";
 *         }
 *         if (fUseTargetColorSpace) {
 *             name += "_cs";
 *         }
 *         if (fUseSubset) {
 *             name += "_domain";
 *         }
 *         if (fUseCubicSampling) {
 *             name += "_cubic";
 *         }
 *         switch (fImageType) {
 *             case Type::kFromPixmaps:
 *                 name += "_frompixmaps";
 *                 break;
 *             case Type::kFromTextures:
 *                 break;
 *             case Type::kFromGenerator:
 *                 name += "_imggen";
 *                 break;
 *             case Type::kFromImages:
 *                 name += "_fromimages";
 *                 break;
 *         }
 *
 *         return name;
 *     }
 *
 *     SkISize getISize() override {
 *         int numCols = 2 * (kLastEnum_SkYUVColorSpace + 1)/2; // opacity x #-color-spaces/2
 *         int numRows = 1 + (kLast_YUVFormat + 1);  // original + #-yuv-formats
 *         int wh = SkScalarCeilToInt(kTileWidthHeight * (fUseSubset ? 1.5f : 1.f));
 *         return SkISize::Make(kLabelWidth  + numCols * (wh + kPad),
 *                              kLabelHeight + numRows * (wh + kPad));
 *     }
 *
 *     void createBitmaps() {
 *         SkPoint origin = { kTileWidthHeight/2.0f, kTileWidthHeight/2.0f };
 *         float outerRadius = kTileWidthHeight/2.0f - 20.0f;
 *         float innerRadius = 20.0f;
 *
 *         {
 *             // transparent
 *             SkTDArray<SkRect> circles;
 *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 5, &circles);
 *             fOriginalBMs[0] = make_bitmap(kRGBA_8888_SkColorType, path, circles, false, fUseSubset);
 *         }
 *
 *         {
 *             // opaque
 *             SkTDArray<SkRect> circles;
 *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 7, &circles);
 *             fOriginalBMs[1] = make_bitmap(kRGBA_8888_SkColorType, path, circles, true, fUseSubset);
 *         }
 *
 *         if (fUseTargetColorSpace) {
 *             fTargetColorSpace = SkColorSpace::MakeSRGB()->makeColorSpin();
 *         }
 *     }
 *
 *     bool createImages(GrDirectContext* dContext, Recorder* recorder) {
 *         int origin = 0;
 *         for (bool opaque : { false, true }) {
 *             for (int cs = kJPEG_SkYUVColorSpace; cs <= kLastEnum_SkYUVColorSpace; ++cs) {
 *                 if (fUseLimitedRange !=
 *                     SkYUVColorSpaceIsLimitedRange(static_cast<SkYUVColorSpace>(cs))) {
 *                     continue;
 *                 }
 *
 *                 PlaneData planes;
 *                 extract_planes(fOriginalBMs[opaque],
 *                                static_cast<SkYUVColorSpace>(cs),
 *                                static_cast<SkEncodedOrigin>(origin + 1),  // valid origins are 1...8
 *                                &planes);
 *
 *                 for (int f = kP016_YUVFormat; f <= kLast_YUVFormat; ++f) {
 *                     auto format = static_cast<YUVFormat>(f);
 *                     SkBitmap resultBMs[4];
 *
 *                     int numPlanes = create_YUV(planes, format, resultBMs, opaque);
 *                     const YUVAPlanarConfig planarConfig(format,
 *                                                         opaque,
 *                                                         static_cast<SkEncodedOrigin>(origin + 1));
 *                     SkYUVAPixmaps pixmaps =
 *                             planarConfig.makeYUVAPixmaps(fOriginalBMs[opaque].dimensions(),
 *                                                          static_cast<SkYUVColorSpace>(cs),
 *                                                          resultBMs,
 *                                                          numPlanes);
 *                     auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(std::move(pixmaps));
 * #if defined(SK_GRAPHITE)
 *                     if (recorder) {
 *                         fImages[opaque][cs][format] = lazyYUV->refImage(recorder, fImageType);
 *                     } else
 * #endif
 *                     {
 *                         fImages[opaque][cs][format] = lazyYUV->refImage(dContext, fImageType);
 *                     }
 *                 }
 *                 origin = (origin + 1) % 8;
 *             }
 *         }
 *
 * #if defined(SK_GANESH)
 *         if (dContext) {
 *             // Some backends (e.g., Vulkan) require all work be completed for backend textures
 *             // before they are deleted. Since we don't know when we'll next have access to a
 *             // direct context, flush all the work now.
 *             dContext->flush();
 *             dContext->submit(GrSyncCpu::kYes);
 *         }
 * #endif
 *
 *         return true;
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 *         GrDirectContext* dContext = nullptr;
 * #if defined(SK_GANESH)
 *         dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext && dContext->abandoned()) {
 *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
 *             // if forbidden.
 *             return DrawResult::kSkip;
 *         }
 * #endif
 *         auto recorder = canvas->recorder();
 *         this->createBitmaps();
 *
 *         // Only the generator is expected to work with the CPU backend.
 *         if (fImageType != Type::kFromGenerator && !dContext && !recorder) {
 *             return DrawResult::kSkip;
 *         }
 *
 *         if (!this->createImages(dContext, recorder)) {
 *             *errorMsg = "Failed to create YUV images";
 *             return DrawResult::kFail;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override {
 *         for (int i = 0; i < 2; ++i) {
 *             for (int j = 0; j <= kLastEnum_SkYUVColorSpace; ++j) {
 *                 for (int k = 0; k <= kLast_YUVFormat; ++k) {
 *                     fImages[i][j][k] = nullptr;
 *                 }
 *             }
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto recorder = canvas->baseRecorder();
 *         SkASSERT(recorder);
 *
 *         float cellWidth = kTileWidthHeight, cellHeight = kTileWidthHeight;
 *         if (fUseSubset) {
 *             cellWidth *= 1.5f;
 *             cellHeight *= 1.5f;
 *         }
 *
 *         SkRect srcRect = SkRect::Make(fOriginalBMs[0].dimensions());
 *         SkRect dstRect = SkRect::MakeXYWH(kLabelWidth, 0.f, srcRect.width(), srcRect.height());
 *
 *         SkCanvas::SrcRectConstraint constraint = SkCanvas::kFast_SrcRectConstraint;
 *         if (fUseSubset) {
 *             srcRect.inset(kSubsetPadding, kSubsetPadding);
 *             // Draw a larger rectangle to ensure bilerp filtering would normally read outside the
 *             // srcRect and hit the red pixels, if strict constraint weren't used.
 *             dstRect.fRight = kLabelWidth + 1.5f * srcRect.width();
 *             dstRect.fBottom = 1.5f * srcRect.height();
 *             constraint = SkCanvas::kStrict_SrcRectConstraint;
 *         }
 *
 *         SkSamplingOptions sampling = fUseCubicSampling
 *                                          ? SkSamplingOptions(SkCubicResampler::Mitchell())
 *                                          : SkSamplingOptions(SkFilterMode::kLinear);
 *         for (int cs = kJPEG_SkYUVColorSpace; cs <= kLastEnum_SkYUVColorSpace; ++cs) {
 *             if (fUseLimitedRange !=
 *                 SkYUVColorSpaceIsLimitedRange(static_cast<SkYUVColorSpace>(cs))) {
 *                 continue;
 *             }
 *
 *             SkPaint paint;
 *             if (kIdentity_SkYUVColorSpace == cs) {
 *                 // The identity color space needs post processing to appear correctly
 *                 paint.setColorFilter(yuv_to_rgb_colorfilter());
 *             }
 *
 *             for (int opaque : { 0, 1 }) {
 *                 dstRect.offsetTo(dstRect.fLeft, kLabelHeight);
 *
 *                 draw_col_label(canvas, dstRect.fLeft + cellWidth / 2, cs, opaque);
 *
 *                 canvas->drawImageRect(fOriginalBMs[opaque].asImage(), srcRect, dstRect,
 *                                       SkSamplingOptions(), nullptr, constraint);
 *                 dstRect.offset(0.f, cellHeight + kPad);
 *
 *                 for (int format = kP016_YUVFormat; format <= kLast_YUVFormat; ++format) {
 *                     draw_row_label(canvas, dstRect.fTop, format);
 *                     if (fUseTargetColorSpace && fImages[opaque][cs][format]) {
 *                         // Making a CS-specific version of a kIdentity_SkYUVColorSpace YUV image
 *                         // doesn't make a whole lot of sense. The colorSpace conversion will
 *                         // operate on the YUV components rather than the RGB components.
 *                         sk_sp<SkImage> csImage = fImages[opaque][cs][format]->makeColorSpace(
 *                                 recorder, fTargetColorSpace, {});
 *                         canvas->drawImageRect(csImage, srcRect, dstRect, sampling,
 *                                               &paint, constraint);
 *                     } else {
 *                         canvas->drawImageRect(fImages[opaque][cs][format], srcRect, dstRect,
 *                                               sampling, &paint, constraint);
 *                     }
 *                     dstRect.offset(0.f, cellHeight + kPad);
 *                 }
 *
 *                 dstRect.offset(cellWidth + kPad, 0.f);
 *             }
 *         }
 *     }
 *
 * private:
 *     SkBitmap                   fOriginalBMs[2];
 *     sk_sp<SkImage>             fImages[2][kLastEnum_SkYUVColorSpace + 1][kLast_YUVFormat + 1];
 *     bool                       fUseLimitedRange;
 *     bool                       fUseTargetColorSpace;
 *     bool                       fUseSubset;
 *     bool                       fUseCubicSampling;
 *     Type                       fImageType;
 *     sk_sp<SkColorSpace>        fTargetColorSpace;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class WackyYUVFormatsGM public constructor(
  useLimitedRange: Boolean,
  useTargetColorSpace: Boolean,
  useSubset: Boolean,
  useCubicSampling: Boolean,
  type: WackyYUVFormatsGMType,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap                   fOriginalBMs[2]
   * ```
   */
  private var fOriginalBMs: Array<SkBitmap> = TODO("Initialize fOriginalBMs")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>             fImages[2][kLastEnum_SkYUVColorSpace + 1][kLast_YUVFormat + 1]
   * ```
   */
  private var fImages: Array<SkSp<SkImage>> = TODO("Initialize fImages")

  /**
   * C++ original:
   * ```cpp
   * bool                       fUseLimitedRange
   * ```
   */
  private var fUseLimitedRange: Boolean = TODO("Initialize fUseLimitedRange")

  /**
   * C++ original:
   * ```cpp
   * bool                       fUseTargetColorSpace
   * ```
   */
  private var fUseTargetColorSpace: Boolean = TODO("Initialize fUseTargetColorSpace")

  /**
   * C++ original:
   * ```cpp
   * bool                       fUseSubset
   * ```
   */
  private var fUseSubset: Boolean = TODO("Initialize fUseSubset")

  /**
   * C++ original:
   * ```cpp
   * bool                       fUseCubicSampling
   * ```
   */
  private var fUseCubicSampling: Boolean = TODO("Initialize fUseCubicSampling")

  /**
   * C++ original:
   * ```cpp
   * Type                       fImageType
   * ```
   */
  private var fImageType: WackyYUVFormatsGMType = TODO("Initialize fImageType")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace>        fTargetColorSpace
   * ```
   */
  private var fTargetColorSpace: SkSp<SkColorSpace> = TODO("Initialize fTargetColorSpace")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("wacky_yuv_formats");
   *         if (fUseLimitedRange) {
   *             name += "_limited";
   *         }
   *         if (fUseTargetColorSpace) {
   *             name += "_cs";
   *         }
   *         if (fUseSubset) {
   *             name += "_domain";
   *         }
   *         if (fUseCubicSampling) {
   *             name += "_cubic";
   *         }
   *         switch (fImageType) {
   *             case Type::kFromPixmaps:
   *                 name += "_frompixmaps";
   *                 break;
   *             case Type::kFromTextures:
   *                 break;
   *             case Type::kFromGenerator:
   *                 name += "_imggen";
   *                 break;
   *             case Type::kFromImages:
   *                 name += "_fromimages";
   *                 break;
   *         }
   *
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
   * SkISize getISize() override {
   *         int numCols = 2 * (kLastEnum_SkYUVColorSpace + 1)/2; // opacity x #-color-spaces/2
   *         int numRows = 1 + (kLast_YUVFormat + 1);  // original + #-yuv-formats
   *         int wh = SkScalarCeilToInt(kTileWidthHeight * (fUseSubset ? 1.5f : 1.f));
   *         return SkISize::Make(kLabelWidth  + numCols * (wh + kPad),
   *                              kLabelHeight + numRows * (wh + kPad));
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void createBitmaps() {
   *         SkPoint origin = { kTileWidthHeight/2.0f, kTileWidthHeight/2.0f };
   *         float outerRadius = kTileWidthHeight/2.0f - 20.0f;
   *         float innerRadius = 20.0f;
   *
   *         {
   *             // transparent
   *             SkTDArray<SkRect> circles;
   *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 5, &circles);
   *             fOriginalBMs[0] = make_bitmap(kRGBA_8888_SkColorType, path, circles, false, fUseSubset);
   *         }
   *
   *         {
   *             // opaque
   *             SkTDArray<SkRect> circles;
   *             SkPath path = create_splat(origin, innerRadius, outerRadius, 1.0f, 7, &circles);
   *             fOriginalBMs[1] = make_bitmap(kRGBA_8888_SkColorType, path, circles, true, fUseSubset);
   *         }
   *
   *         if (fUseTargetColorSpace) {
   *             fTargetColorSpace = SkColorSpace::MakeSRGB()->makeColorSpin();
   *         }
   *     }
   * ```
   */
  protected fun createBitmaps() {
    TODO("Implement createBitmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool createImages(GrDirectContext* dContext, Recorder* recorder) {
   *         int origin = 0;
   *         for (bool opaque : { false, true }) {
   *             for (int cs = kJPEG_SkYUVColorSpace; cs <= kLastEnum_SkYUVColorSpace; ++cs) {
   *                 if (fUseLimitedRange !=
   *                     SkYUVColorSpaceIsLimitedRange(static_cast<SkYUVColorSpace>(cs))) {
   *                     continue;
   *                 }
   *
   *                 PlaneData planes;
   *                 extract_planes(fOriginalBMs[opaque],
   *                                static_cast<SkYUVColorSpace>(cs),
   *                                static_cast<SkEncodedOrigin>(origin + 1),  // valid origins are 1...8
   *                                &planes);
   *
   *                 for (int f = kP016_YUVFormat; f <= kLast_YUVFormat; ++f) {
   *                     auto format = static_cast<YUVFormat>(f);
   *                     SkBitmap resultBMs[4];
   *
   *                     int numPlanes = create_YUV(planes, format, resultBMs, opaque);
   *                     const YUVAPlanarConfig planarConfig(format,
   *                                                         opaque,
   *                                                         static_cast<SkEncodedOrigin>(origin + 1));
   *                     SkYUVAPixmaps pixmaps =
   *                             planarConfig.makeYUVAPixmaps(fOriginalBMs[opaque].dimensions(),
   *                                                          static_cast<SkYUVColorSpace>(cs),
   *                                                          resultBMs,
   *                                                          numPlanes);
   *                     auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(std::move(pixmaps));
   * #if defined(SK_GRAPHITE)
   *                     if (recorder) {
   *                         fImages[opaque][cs][format] = lazyYUV->refImage(recorder, fImageType);
   *                     } else
   * #endif
   *                     {
   *                         fImages[opaque][cs][format] = lazyYUV->refImage(dContext, fImageType);
   *                     }
   *                 }
   *                 origin = (origin + 1) % 8;
   *             }
   *         }
   *
   * #if defined(SK_GANESH)
   *         if (dContext) {
   *             // Some backends (e.g., Vulkan) require all work be completed for backend textures
   *             // before they are deleted. Since we don't know when we'll next have access to a
   *             // direct context, flush all the work now.
   *             dContext->flush();
   *             dContext->submit(GrSyncCpu::kYes);
   *         }
   * #endif
   *
   *         return true;
   *     }
   * ```
   */
  protected fun createImages(dContext: GrDirectContext?, recorder: Recorder?): Boolean {
    TODO("Implement createImages")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   *         GrDirectContext* dContext = nullptr;
   * #if defined(SK_GANESH)
   *         dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext && dContext->abandoned()) {
   *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
   *             // if forbidden.
   *             return DrawResult::kSkip;
   *         }
   * #endif
   *         auto recorder = canvas->recorder();
   *         this->createBitmaps();
   *
   *         // Only the generator is expected to work with the CPU backend.
   *         if (fImageType != Type::kFromGenerator && !dContext && !recorder) {
   *             return DrawResult::kSkip;
   *         }
   *
   *         if (!this->createImages(dContext, recorder)) {
   *             *errorMsg = "Failed to create YUV images";
   *             return DrawResult::kFail;
   *         }
   *
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onGpuSetup(
    canvas: SkCanvas?,
    errorMsg: String?,
    param2: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override {
   *         for (int i = 0; i < 2; ++i) {
   *             for (int j = 0; j <= kLastEnum_SkYUVColorSpace; ++j) {
   *                 for (int k = 0; k <= kLast_YUVFormat; ++k) {
   *                     fImages[i][j][k] = nullptr;
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onGpuTeardown() {
    TODO("Implement onGpuTeardown")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         auto recorder = canvas->baseRecorder();
   *         SkASSERT(recorder);
   *
   *         float cellWidth = kTileWidthHeight, cellHeight = kTileWidthHeight;
   *         if (fUseSubset) {
   *             cellWidth *= 1.5f;
   *             cellHeight *= 1.5f;
   *         }
   *
   *         SkRect srcRect = SkRect::Make(fOriginalBMs[0].dimensions());
   *         SkRect dstRect = SkRect::MakeXYWH(kLabelWidth, 0.f, srcRect.width(), srcRect.height());
   *
   *         SkCanvas::SrcRectConstraint constraint = SkCanvas::kFast_SrcRectConstraint;
   *         if (fUseSubset) {
   *             srcRect.inset(kSubsetPadding, kSubsetPadding);
   *             // Draw a larger rectangle to ensure bilerp filtering would normally read outside the
   *             // srcRect and hit the red pixels, if strict constraint weren't used.
   *             dstRect.fRight = kLabelWidth + 1.5f * srcRect.width();
   *             dstRect.fBottom = 1.5f * srcRect.height();
   *             constraint = SkCanvas::kStrict_SrcRectConstraint;
   *         }
   *
   *         SkSamplingOptions sampling = fUseCubicSampling
   *                                          ? SkSamplingOptions(SkCubicResampler::Mitchell())
   *                                          : SkSamplingOptions(SkFilterMode::kLinear);
   *         for (int cs = kJPEG_SkYUVColorSpace; cs <= kLastEnum_SkYUVColorSpace; ++cs) {
   *             if (fUseLimitedRange !=
   *                 SkYUVColorSpaceIsLimitedRange(static_cast<SkYUVColorSpace>(cs))) {
   *                 continue;
   *             }
   *
   *             SkPaint paint;
   *             if (kIdentity_SkYUVColorSpace == cs) {
   *                 // The identity color space needs post processing to appear correctly
   *                 paint.setColorFilter(yuv_to_rgb_colorfilter());
   *             }
   *
   *             for (int opaque : { 0, 1 }) {
   *                 dstRect.offsetTo(dstRect.fLeft, kLabelHeight);
   *
   *                 draw_col_label(canvas, dstRect.fLeft + cellWidth / 2, cs, opaque);
   *
   *                 canvas->drawImageRect(fOriginalBMs[opaque].asImage(), srcRect, dstRect,
   *                                       SkSamplingOptions(), nullptr, constraint);
   *                 dstRect.offset(0.f, cellHeight + kPad);
   *
   *                 for (int format = kP016_YUVFormat; format <= kLast_YUVFormat; ++format) {
   *                     draw_row_label(canvas, dstRect.fTop, format);
   *                     if (fUseTargetColorSpace && fImages[opaque][cs][format]) {
   *                         // Making a CS-specific version of a kIdentity_SkYUVColorSpace YUV image
   *                         // doesn't make a whole lot of sense. The colorSpace conversion will
   *                         // operate on the YUV components rather than the RGB components.
   *                         sk_sp<SkImage> csImage = fImages[opaque][cs][format]->makeColorSpace(
   *                                 recorder, fTargetColorSpace, {});
   *                         canvas->drawImageRect(csImage, srcRect, dstRect, sampling,
   *                                               &paint, constraint);
   *                     } else {
   *                         canvas->drawImageRect(fImages[opaque][cs][format], srcRect, dstRect,
   *                                               sampling, &paint, constraint);
   *                     }
   *                     dstRect.offset(0.f, cellHeight + kPad);
   *                 }
   *
   *                 dstRect.offset(cellWidth + kPad, 0.f);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
