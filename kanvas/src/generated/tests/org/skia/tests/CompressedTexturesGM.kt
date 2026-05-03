package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.math.SkISize
import org.skia.math.SkIVector
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class CompressedTexturesGM : public skiagm::GM {
 * public:
 *     enum class Type {
 *         kNormal,
 *         kNonPowerOfTwo,
 *         kNonMultipleOfFour
 *     };
 *
 *     CompressedTexturesGM(Type type) : fType(type) {
 *         this->setBGColor(0xFFCCCCCC);
 *
 *         switch (fType) {
 *             case Type::kNonPowerOfTwo:
 *                 // These dimensions force the top two mip levels to be 1x3 and 1x1
 *                 fImgDimensions.set(20, 60);
 *                 break;
 *             case Type::kNonMultipleOfFour:
 *                 // These dimensions force the top three mip levels to be 1x7, 1x3 and 1x1
 *                 fImgDimensions.set(13, 61); // prime numbers - just bc
 *                 break;
 *             default:
 *                 fImgDimensions.set(kBaseTexWidth, kBaseTexHeight);
 *                 break;
 *         }
 *
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("compressed_textures");
 *
 *         if (fType == Type::kNonPowerOfTwo) {
 *             name.append("_npot");
 *         } else if (fType == Type::kNonMultipleOfFour) {
 *             name.append("_nmof");
 *         }
 *
 *         return name;
 *     }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(2*kCellWidth + 3*kPad, 2*kBaseTexHeight + 3*kPad);
 *     }
 *
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg,
 *                           GraphiteTestContext* graphiteTestContext) override {
 * #if defined(SK_GANESH)
 *         auto dContext = GrAsDirectContext(canvas->recordingContext());
 *         if (dContext && dContext->abandoned()) {
 *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
 *             // if forbidden.
 *             return DrawResult::kSkip;
 *         }
 * #endif
 *
 *         if (fType == Type::kNonMultipleOfFour) {
 * #if defined(SK_GANESH)
 *             if (dContext && dContext->backend() == GrBackendApi::kDirect3D) {
 *                 // skbug.com/40041877 - Are non-multiple-of-four BC1 textures supported in D3D?
 *                 return DrawResult::kSkip;
 *             }
 * #endif
 * #if defined(SK_GRAPHITE)
 *             skgpu::graphite::Recorder* recorder = canvas->recorder();
 *             if (recorder && recorder->backend() == skgpu::BackendApi::kDawn) {
 *                 // Dawn does not support non-multiple-of-four textures at all. For the same reason
 *                 // we can't support it on older D3D devices above, neither can Dawn. However, Dawn
 *                 // disables support for all devices to keep functionality uniform.
 *                 return DrawResult::kSkip;
 *             }
 * #endif
 *         }
 *
 *         fOpaqueETC2Image = make_compressed_image(canvas, fImgDimensions,
 *                                                  kRGB_565_SkColorType, true,
 *                                                  SkTextureCompressionType::kETC2_RGB8_UNORM);
 *
 *         fOpaqueBC1Image = make_compressed_image(canvas, fImgDimensions,
 *                                                 kRGBA_8888_SkColorType, true,
 *                                                 SkTextureCompressionType::kBC1_RGB8_UNORM);
 *
 *         fTransparentBC1Image = make_compressed_image(canvas, fImgDimensions,
 *                                                      kRGBA_8888_SkColorType, false,
 *                                                      SkTextureCompressionType::kBC1_RGBA8_UNORM);
 *
 *         if (!fOpaqueETC2Image.fImage || !fOpaqueBC1Image.fImage || !fTransparentBC1Image.fImage) {
 *             *errorMsg = "Failed to create compressed images.";
 *             return DrawResult::kFail;
 *         }
 *
 *         return DrawResult::kOk;
 *     }
 *
 *     void onGpuTeardown() override {
 *         fOpaqueETC2Image.fImage = nullptr;
 *         fOpaqueBC1Image.fImage = nullptr;
 *         fTransparentBC1Image.fImage = nullptr;
 *         fOpaqueETC2Image.fGraphiteTexture = nullptr;
 *         fOpaqueBC1Image.fGraphiteTexture = nullptr;
 *         fTransparentBC1Image.fGraphiteTexture = nullptr;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->drawCell(canvas, fOpaqueETC2Image.fImage.get(), { kPad, kPad });
 *
 *         this->drawCell(canvas, fOpaqueBC1Image.fImage.get(), { 2*kPad + kCellWidth, kPad });
 *
 *         this->drawCell(canvas, fTransparentBC1Image.fImage.get(),
 *                        { 2*kPad + kCellWidth, 2*kPad + kBaseTexHeight });
 *     }
 *
 * private:
 *     void drawCell(SkCanvas* canvas, SkImage* image, SkIVector offset) {
 *
 *         SkISize levelDimensions = fImgDimensions;
 *         int numMipLevels = SkMipmap::ComputeLevelCount(levelDimensions) + 1;
 *
 *         SkSamplingOptions sampling(SkCubicResampler::Mitchell());
 *
 *         bool isCompressed = false;
 *         if (image->isTextureBacked()) {
 * #if defined(SK_GANESH)
 *             if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *                 const GrCaps* caps = as_IB(image)->context()->priv().caps();
 *                 GrTextureProxy* proxy = sk_gpu_test::GetTextureImageProxy(image, dContext);
 *                 isCompressed = caps->isFormatCompressed(proxy->backendFormat());
 *             } else
 * #endif
 *             {
 *                 // Graphite has no fallback to upload the compressed data to a non-compressed
 *                 // format. So if the image is texture backed and graphite then it will be a
 *                 // compressed format.
 *                 isCompressed = true;
 *             }
 *         }
 *
 *         SkPaint redStrokePaint;
 *         redStrokePaint.setColor(SK_ColorRED);
 *         redStrokePaint.setStyle(SkPaint::kStroke_Style);
 *
 *         for (int i = 0; i < numMipLevels; ++i) {
 *             SkRect r = SkRect::MakeXYWH(offset.fX, offset.fY,
 *                                         levelDimensions.width(), levelDimensions.height());
 *
 *             canvas->drawImageRect(image, r, sampling);
 *             if (!isCompressed) {
 *                 // Make it obvious which drawImages used decompressed images
 *                 canvas->drawRect(r, redStrokePaint);
 *             }
 *
 *             if (i == 0) {
 *                 offset.fX += levelDimensions.width()+1;
 *             } else {
 *                 offset.fY += levelDimensions.height()+1;
 *             }
 *
 *             levelDimensions = {std::max(1, levelDimensions.width()/2),
 *                                std::max(1, levelDimensions.height()/2)};
 *         }
 *     }
 *
 *     static const int kPad = 8;
 *     static const int kBaseTexWidth = 64;
 *     static const int kCellWidth = 1.5f * kBaseTexWidth;
 *     static const int kBaseTexHeight = 64;
 *
 *     Type           fType;
 *     SkISize        fImgDimensions;
 *
 *
 *     CompressedImageObjects fOpaqueETC2Image;
 *     CompressedImageObjects fOpaqueBC1Image;
 *     CompressedImageObjects fTransparentBC1Image;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class CompressedTexturesGM public constructor(
  type: Type,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * static const int kPad = 8
   * ```
   */
  private var fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * static const int kBaseTexWidth = 64
   * ```
   */
  private var fImgDimensions: SkISize = TODO("Initialize fImgDimensions")

  /**
   * C++ original:
   * ```cpp
   * static const int kCellWidth = 1.5f * kBaseTexWidth
   * ```
   */
  private var fOpaqueETC2Image: CompressedImageObjects = TODO("Initialize fOpaqueETC2Image")

  /**
   * C++ original:
   * ```cpp
   * static const int kBaseTexHeight = 64
   * ```
   */
  private var fOpaqueBC1Image: CompressedImageObjects = TODO("Initialize fOpaqueBC1Image")

  /**
   * C++ original:
   * ```cpp
   * Type           fType
   * ```
   */
  private var fTransparentBC1Image: CompressedImageObjects = TODO("Initialize fTransparentBC1Image")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("compressed_textures");
   *
   *         if (fType == Type::kNonPowerOfTwo) {
   *             name.append("_npot");
   *         } else if (fType == Type::kNonMultipleOfFour) {
   *             name.append("_nmof");
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
   *         return SkISize::Make(2*kCellWidth + 3*kPad, 2*kBaseTexHeight + 3*kPad);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onGpuSetup(SkCanvas* canvas, SkString* errorMsg,
   *                           GraphiteTestContext* graphiteTestContext) override {
   * #if defined(SK_GANESH)
   *         auto dContext = GrAsDirectContext(canvas->recordingContext());
   *         if (dContext && dContext->abandoned()) {
   *             // This isn't a GpuGM so a null 'context' is okay but an abandoned context
   *             // if forbidden.
   *             return DrawResult::kSkip;
   *         }
   * #endif
   *
   *         if (fType == Type::kNonMultipleOfFour) {
   * #if defined(SK_GANESH)
   *             if (dContext && dContext->backend() == GrBackendApi::kDirect3D) {
   *                 // skbug.com/40041877 - Are non-multiple-of-four BC1 textures supported in D3D?
   *                 return DrawResult::kSkip;
   *             }
   * #endif
   * #if defined(SK_GRAPHITE)
   *             skgpu::graphite::Recorder* recorder = canvas->recorder();
   *             if (recorder && recorder->backend() == skgpu::BackendApi::kDawn) {
   *                 // Dawn does not support non-multiple-of-four textures at all. For the same reason
   *                 // we can't support it on older D3D devices above, neither can Dawn. However, Dawn
   *                 // disables support for all devices to keep functionality uniform.
   *                 return DrawResult::kSkip;
   *             }
   * #endif
   *         }
   *
   *         fOpaqueETC2Image = make_compressed_image(canvas, fImgDimensions,
   *                                                  kRGB_565_SkColorType, true,
   *                                                  SkTextureCompressionType::kETC2_RGB8_UNORM);
   *
   *         fOpaqueBC1Image = make_compressed_image(canvas, fImgDimensions,
   *                                                 kRGBA_8888_SkColorType, true,
   *                                                 SkTextureCompressionType::kBC1_RGB8_UNORM);
   *
   *         fTransparentBC1Image = make_compressed_image(canvas, fImgDimensions,
   *                                                      kRGBA_8888_SkColorType, false,
   *                                                      SkTextureCompressionType::kBC1_RGBA8_UNORM);
   *
   *         if (!fOpaqueETC2Image.fImage || !fOpaqueBC1Image.fImage || !fTransparentBC1Image.fImage) {
   *             *errorMsg = "Failed to create compressed images.";
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
    graphiteTestContext: GraphiteTestContext?,
  ): DrawResult {
    TODO("Implement onGpuSetup")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGpuTeardown() override {
   *         fOpaqueETC2Image.fImage = nullptr;
   *         fOpaqueBC1Image.fImage = nullptr;
   *         fTransparentBC1Image.fImage = nullptr;
   *         fOpaqueETC2Image.fGraphiteTexture = nullptr;
   *         fOpaqueBC1Image.fGraphiteTexture = nullptr;
   *         fTransparentBC1Image.fGraphiteTexture = nullptr;
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
   *         this->drawCell(canvas, fOpaqueETC2Image.fImage.get(), { kPad, kPad });
   *
   *         this->drawCell(canvas, fOpaqueBC1Image.fImage.get(), { 2*kPad + kCellWidth, kPad });
   *
   *         this->drawCell(canvas, fTransparentBC1Image.fImage.get(),
   *                        { 2*kPad + kCellWidth, 2*kPad + kBaseTexHeight });
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCell(SkCanvas* canvas, SkImage* image, SkIVector offset) {
   *
   *         SkISize levelDimensions = fImgDimensions;
   *         int numMipLevels = SkMipmap::ComputeLevelCount(levelDimensions) + 1;
   *
   *         SkSamplingOptions sampling(SkCubicResampler::Mitchell());
   *
   *         bool isCompressed = false;
   *         if (image->isTextureBacked()) {
   * #if defined(SK_GANESH)
   *             if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
   *                 const GrCaps* caps = as_IB(image)->context()->priv().caps();
   *                 GrTextureProxy* proxy = sk_gpu_test::GetTextureImageProxy(image, dContext);
   *                 isCompressed = caps->isFormatCompressed(proxy->backendFormat());
   *             } else
   * #endif
   *             {
   *                 // Graphite has no fallback to upload the compressed data to a non-compressed
   *                 // format. So if the image is texture backed and graphite then it will be a
   *                 // compressed format.
   *                 isCompressed = true;
   *             }
   *         }
   *
   *         SkPaint redStrokePaint;
   *         redStrokePaint.setColor(SK_ColorRED);
   *         redStrokePaint.setStyle(SkPaint::kStroke_Style);
   *
   *         for (int i = 0; i < numMipLevels; ++i) {
   *             SkRect r = SkRect::MakeXYWH(offset.fX, offset.fY,
   *                                         levelDimensions.width(), levelDimensions.height());
   *
   *             canvas->drawImageRect(image, r, sampling);
   *             if (!isCompressed) {
   *                 // Make it obvious which drawImages used decompressed images
   *                 canvas->drawRect(r, redStrokePaint);
   *             }
   *
   *             if (i == 0) {
   *                 offset.fX += levelDimensions.width()+1;
   *             } else {
   *                 offset.fY += levelDimensions.height()+1;
   *             }
   *
   *             levelDimensions = {std::max(1, levelDimensions.width()/2),
   *                                std::max(1, levelDimensions.height()/2)};
   *         }
   *     }
   * ```
   */
  private fun drawCell(
    canvas: SkCanvas?,
    image: SkImage?,
    offset: SkIVector,
  ) {
    TODO("Implement drawCell")
  }

  public enum class Type {
    kNormal,
    kNonPowerOfTwo,
    kNonMultipleOfFour,
  }

  public companion object {
    private val kPad: Int = TODO("Initialize kPad")

    private val kBaseTexWidth: Int = TODO("Initialize kBaseTexWidth")

    private val kCellWidth: Int = TODO("Initialize kCellWidth")

    private val kBaseTexHeight: Int = TODO("Initialize kBaseTexHeight")
  }
}
