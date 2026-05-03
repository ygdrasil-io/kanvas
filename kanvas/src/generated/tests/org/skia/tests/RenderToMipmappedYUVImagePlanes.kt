package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class RenderToMipmappedYUVImagePlanes : public GM {
 * public:
 *     RenderToMipmappedYUVImagePlanes() { this->setBGColor(0xFFFFFFFF); }
 *
 * protected:
 *     SkString getName() const override { return SkString("render_to_mipmapped_yuv_image_planes"); }
 *
 *     SkISize getISize() override { return {96, 32}; }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
 *         auto* recorder = canvas->recorder();
 *         if (!recorder) {
 *             *errorMsg = "graphite-only test";
 *             return DrawResult::kSkip;
 *         }
 *         if (!fSrcImage) {
 *             fSrcImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
 *             if (!fSrcImage) {
 *                 *errorMsg = "Could not load src image.";
 *                 return DrawResult::kFail;
 *             }
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         auto* recorder = canvas->recorder();
 *         if (!recorder) {
 *             *errorMsg = "direct to graphite test";
 *             return DrawResult::kSkip;
 *         }
 *         using PlaneConfig = SkYUVAInfo::PlaneConfig;
 *         using Subsampling = SkYUVAInfo::Subsampling;
 *         struct TestCase {
 *             PlaneConfig config;
 *             Subsampling subsampling;
 *         };
 *         for (const auto& tc : {TestCase{PlaneConfig::kY_U_V, Subsampling::k420},
 *                                TestCase{PlaneConfig::kY_UV,  Subsampling::k422},
 *                                TestCase{PlaneConfig::kYUV,   Subsampling::k444}}) {
 *             SkYUVAInfo yuvaInfo(fSrcImage->dimensions(),
 *                                 tc.config,
 *                                 tc.subsampling,
 *                                 kJPEG_Full_SkYUVColorSpace);
 *
 *             float rgbToYuv[20];
 *             SkColorMatrix::RGBtoYUV(yuvaInfo.yuvColorSpace()).getRowMajor(rgbToYuv);
 *             sk_sp<SkImage> planes[SkYUVAInfo::kMaxPlanes];
 *             SkISize dimensions[SkYUVAInfo::kMaxPlanes];
 *             int numPlanes = yuvaInfo.planeDimensions(dimensions);
 *
 *             SkColorType colorTypes  [SkYUVAInfo::kMaxPlanes];
 *             uint32_t    channelFlags[SkYUVAInfo::kMaxPlanes];
 *             for (int i = 0; i < numPlanes; ++i) {
 *                 switch (yuvaInfo.numChannelsInPlane(i)) {
 *                     case 1: colorTypes[i] = kAlpha_8_SkColorType;    break;
 *                     case 2: colorTypes[i] = kR8G8_unorm_SkColorType; break;
 *                     case 3: colorTypes[i] = kRGB_888x_SkColorType;   break;
 *                     case 4: colorTypes[i] = kRGBA_8888_SkColorType;  break;
 *
 *                     default: SkUNREACHABLE;
 *                 }
 *                 channelFlags[i] = SkColorTypeChannelFlags(colorTypes[i]);
 *             }
 *             SkYUVAInfo::YUVALocations locations = yuvaInfo.toYUVALocations(channelFlags);
 *
 *             for (int i = 0; i < numPlanes; ++i) {
 *                 auto info = SkImageInfo::Make(dimensions[i], colorTypes[i], kPremul_SkAlphaType);
 *                 auto surf = SkSurfaces::RenderTarget(recorder, info, skgpu::Mipmapped::kYes);
 *                 if (!surf) {
 *                     continue;
 *                 }
 *
 *                 float matrix[20] {
 *                     1, 0, 0, 0, 0,
 *                     0, 1, 0, 0, 0,
 *                     0, 0, 1, 0, 0,
 *                     0, 0, 0, 1, 0
 *                 };
 *
 *                 for (int c = 0; c < SkYUVAInfo::kYUVAChannelCount; ++c) {
 *                     if (locations[c].fPlane == i) {
 *                         auto d = static_cast<int>(locations[c].fChannel);
 *                         std::copy_n(rgbToYuv + 5 * c, 5, matrix + 5 * d);
 *                     }
 *                 }
 *
 *                 auto cf = SkColorFilters::Matrix(matrix);
 *                 SkPaint paint;
 *                 paint.setColorFilter(SkColorFilters::Matrix(matrix));
 *                 surf->getCanvas()->drawImageRect(fSrcImage,
 *                                                  SkRect::Make(surf->imageInfo().dimensions()),
 *                                                  SkFilterMode::kLinear,
 *                                                  &paint);
 *                 planes[i] = SkSurfaces::AsImage(std::move(surf));
 *             }
 *             auto yuvaImage = SkImages::TextureFromYUVAImages(recorder,
 *                                                              yuvaInfo,
 *                                                              planes,
 *                                                              /*imageColorSpace=*/nullptr);
 *             if (yuvaImage) {
 *                 auto dstRect = SkRect::MakeWH(yuvaImage->width() / 16.f,
 *                                               yuvaImage->height() / 16.f);
 *                 canvas->drawImageRect(yuvaImage.get(),
 *                                       dstRect,
 *                                       SkSamplingOptions(SkFilterMode::kLinear,
 *                                                         SkMipmapMode::kLinear));
 *             }
 *             canvas->translate(std::ceil(yuvaInfo.width()/16.f), 0);
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     sk_sp<SkImage> fSrcImage;
 * }
 * ```
 */
public open class RenderToMipmappedYUVImagePlanes public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fSrcImage
   * ```
   */
  private var fSrcImage: SkSp<SkImage> = TODO("Initialize fSrcImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("render_to_mipmapped_yuv_image_planes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {96, 32}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg, GraphiteTestContext*) override {
   *         auto* recorder = canvas->recorder();
   *         if (!recorder) {
   *             *errorMsg = "graphite-only test";
   *             return DrawResult::kSkip;
   *         }
   *         if (!fSrcImage) {
   *             fSrcImage = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
   *             if (!fSrcImage) {
   *                 *errorMsg = "Could not load src image.";
   *                 return DrawResult::kFail;
   *             }
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
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         auto* recorder = canvas->recorder();
   *         if (!recorder) {
   *             *errorMsg = "direct to graphite test";
   *             return DrawResult::kSkip;
   *         }
   *         using PlaneConfig = SkYUVAInfo::PlaneConfig;
   *         using Subsampling = SkYUVAInfo::Subsampling;
   *         struct TestCase {
   *             PlaneConfig config;
   *             Subsampling subsampling;
   *         };
   *         for (const auto& tc : {TestCase{PlaneConfig::kY_U_V, Subsampling::k420},
   *                                TestCase{PlaneConfig::kY_UV,  Subsampling::k422},
   *                                TestCase{PlaneConfig::kYUV,   Subsampling::k444}}) {
   *             SkYUVAInfo yuvaInfo(fSrcImage->dimensions(),
   *                                 tc.config,
   *                                 tc.subsampling,
   *                                 kJPEG_Full_SkYUVColorSpace);
   *
   *             float rgbToYuv[20];
   *             SkColorMatrix::RGBtoYUV(yuvaInfo.yuvColorSpace()).getRowMajor(rgbToYuv);
   *             sk_sp<SkImage> planes[SkYUVAInfo::kMaxPlanes];
   *             SkISize dimensions[SkYUVAInfo::kMaxPlanes];
   *             int numPlanes = yuvaInfo.planeDimensions(dimensions);
   *
   *             SkColorType colorTypes  [SkYUVAInfo::kMaxPlanes];
   *             uint32_t    channelFlags[SkYUVAInfo::kMaxPlanes];
   *             for (int i = 0; i < numPlanes; ++i) {
   *                 switch (yuvaInfo.numChannelsInPlane(i)) {
   *                     case 1: colorTypes[i] = kAlpha_8_SkColorType;    break;
   *                     case 2: colorTypes[i] = kR8G8_unorm_SkColorType; break;
   *                     case 3: colorTypes[i] = kRGB_888x_SkColorType;   break;
   *                     case 4: colorTypes[i] = kRGBA_8888_SkColorType;  break;
   *
   *                     default: SkUNREACHABLE;
   *                 }
   *                 channelFlags[i] = SkColorTypeChannelFlags(colorTypes[i]);
   *             }
   *             SkYUVAInfo::YUVALocations locations = yuvaInfo.toYUVALocations(channelFlags);
   *
   *             for (int i = 0; i < numPlanes; ++i) {
   *                 auto info = SkImageInfo::Make(dimensions[i], colorTypes[i], kPremul_SkAlphaType);
   *                 auto surf = SkSurfaces::RenderTarget(recorder, info, skgpu::Mipmapped::kYes);
   *                 if (!surf) {
   *                     continue;
   *                 }
   *
   *                 float matrix[20] {
   *                     1, 0, 0, 0, 0,
   *                     0, 1, 0, 0, 0,
   *                     0, 0, 1, 0, 0,
   *                     0, 0, 0, 1, 0
   *                 };
   *
   *                 for (int c = 0; c < SkYUVAInfo::kYUVAChannelCount; ++c) {
   *                     if (locations[c].fPlane == i) {
   *                         auto d = static_cast<int>(locations[c].fChannel);
   *                         std::copy_n(rgbToYuv + 5 * c, 5, matrix + 5 * d);
   *                     }
   *                 }
   *
   *                 auto cf = SkColorFilters::Matrix(matrix);
   *                 SkPaint paint;
   *                 paint.setColorFilter(SkColorFilters::Matrix(matrix));
   *                 surf->getCanvas()->drawImageRect(fSrcImage,
   *                                                  SkRect::Make(surf->imageInfo().dimensions()),
   *                                                  SkFilterMode::kLinear,
   *                                                  &paint);
   *                 planes[i] = SkSurfaces::AsImage(std::move(surf));
   *             }
   *             auto yuvaImage = SkImages::TextureFromYUVAImages(recorder,
   *                                                              yuvaInfo,
   *                                                              planes,
   *                                                              /*imageColorSpace=*/nullptr);
   *             if (yuvaImage) {
   *                 auto dstRect = SkRect::MakeWH(yuvaImage->width() / 16.f,
   *                                               yuvaImage->height() / 16.f);
   *                 canvas->drawImageRect(yuvaImage.get(),
   *                                       dstRect,
   *                                       SkSamplingOptions(SkFilterMode::kLinear,
   *                                                         SkMipmapMode::kLinear));
   *             }
   *             canvas->translate(std::ceil(yuvaInfo.width()/16.f), 0);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
