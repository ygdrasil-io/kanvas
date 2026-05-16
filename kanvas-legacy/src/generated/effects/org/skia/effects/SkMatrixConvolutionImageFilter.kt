package org.skia.effects

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.IVector
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkMatrixConvolutionImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkMatrixConvolutionImageFilter(const SkISize& kernelSize, const SkScalar* kernel,
 *                                    SkScalar gain, SkScalar bias, const SkIPoint& kernelOffset,
 *                                    bool convolveAlpha, sk_sp<SkImageFilter> const* input)
 *             : SkImageFilter_Base(input, 1)
 *             , fKernel(kernel, kernelSize.width() * kernelSize.height())
 *             , fKernelSize(kernelSize)
 *             , fKernelOffset({kernelOffset.fX, kernelOffset.fY})
 *             , fGain(gain)
 *             , fBias(bias)
 *             , fConvolveAlpha(convolveAlpha) {
 *         // The public factory should have ensured these before creating this object.
 *         SkASSERT(SkSafeMath::Mul(kernelSize.fWidth, kernelSize.fHeight) <= kLargeKernelSize);
 *         SkASSERT(kernelSize.fWidth >= 1 && kernelSize.fHeight >= 1);
 *         SkASSERT(kernelOffset.fX >= 0 && kernelOffset.fX < kernelSize.fWidth);
 *         SkASSERT(kernelOffset.fY >= 0 && kernelOffset.fY < kernelSize.fHeight);
 *
 *         // Does nothing for small kernels, otherwise encodes kernel into an A8 image.
 *         fKernelBitmap = create_kernel_bitmap(kernelSize, kernel, &fInnerGain, &fInnerBias);
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& bounds) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterMatrixConvolutionImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkMatrixConvolutionImageFilter)
 *
 *     bool onAffectsTransparentBlack() const override {
 *         // affectsTransparentBlack() is conflated with "canComputeFastBounds" and MatrixConvolution
 *         // is unique in that it might not produce unbounded output, but we can't calculate the
 *         // fast bounds because the kernel is applied in device space and no transform is provided
 *         // with that API.
 *         // TODO(skbug.com/40045519): Accept a matrix in computeFastBounds() so that we can handle the
 *         // layer-space kernel case.
 *
 *         // That issue aside, a matrix convolution can affect transparent black when it has a
 *         // non-zero bias and convolves alpha (if it doesn't convolve the alpha channel then the bias
 *         // applied to RGB doesn't matter for transparent black pixels).
 *         // NOTE: The crop image filters that wrap the matrix convolution to apply tile modes will
 *         // reset this property when possible.
 *         return true;
 *     }
 *
 *     skif::FilterResult onFilterImage(const skif::Context& context) const override;
 *
 *     skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     // Helper functions to adjust 'bounds' by the kernel size and offset, either for what would be
 *     // sampled when covering 'bounds', or what could produce values when applied to 'bounds'.
 *     skif::LayerSpace<SkIRect> boundsSampledByKernel(const skif::LayerSpace<SkIRect>& bounds) const;
 *     skif::LayerSpace<SkIRect> boundsAffectedByKernel(const skif::LayerSpace<SkIRect>& bounds) const;
 *
 *     sk_sp<SkShader> createShader(const skif::Context& ctx, sk_sp<SkShader> input) const;
 *
 *     // Original kernel data, preserved for serialization even if it was encoded into fKernelBitmap
 *     TArray<float> fKernel;
 *
 *     // Unlike the majority of image filters, the kernel is applied as-is to the layer-space pixels.
 *     // This means that the kernel size and offset are always in the layer coordinate system.
 *     skif::LayerSpace<SkISize>  fKernelSize;
 *     skif::LayerSpace<skif::IVector> fKernelOffset;
 *
 *     float fGain;
 *     float fBias; // NOTE: This is assumed to be in [0-255] for historical reasons
 *     bool  fConvolveAlpha;
 *
 *     // Derived from fKernel when larger than what we will upload as uniforms; fInnerBias and
 *     // fInnerGain  reconstruct the original coefficient from unorm8 data as (a+innerBias)*innerGain
 *     // Since these are derived, they are not serialized.
 *     SkBitmap fKernelBitmap;
 *     float fInnerBias;
 *     float fInnerGain;
 * }
 * ```
 */
public class SkMatrixConvolutionImageFilter public constructor(
  kernelSize: SkISize,
  kernel: SkScalar?,
  gain: SkScalar,
  bias: SkScalar,
  kernelOffset: SkIPoint,
  convolveAlpha: Boolean,
  input: Any?,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TArray<float> fKernel
   * ```
   */
  private var fKernel: Int = TODO("Initialize fKernel")

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkISize>  fKernelSize
   * ```
   */
  private var fKernelSize: LayerSpace<SkISize> = TODO("Initialize fKernelSize")

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<skif::IVector> fKernelOffset
   * ```
   */
  private var fKernelOffset: LayerSpace<IVector> = TODO("Initialize fKernelOffset")

  /**
   * C++ original:
   * ```cpp
   * float fGain
   * ```
   */
  private var fGain: Float = TODO("Initialize fGain")

  /**
   * C++ original:
   * ```cpp
   * float fBias
   * ```
   */
  private var fBias: Float = TODO("Initialize fBias")

  /**
   * C++ original:
   * ```cpp
   * bool  fConvolveAlpha
   * ```
   */
  private var fConvolveAlpha: Boolean = TODO("Initialize fConvolveAlpha")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fKernelBitmap
   * ```
   */
  private var fKernelBitmap: SkBitmap = TODO("Initialize fKernelBitmap")

  /**
   * C++ original:
   * ```cpp
   * float fInnerBias
   * ```
   */
  private var fInnerBias: Float = TODO("Initialize fInnerBias")

  /**
   * C++ original:
   * ```cpp
   * float fInnerGain
   * ```
   */
  private var fInnerGain: Float = TODO("Initialize fInnerGain")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkMatrixConvolutionImageFilter::computeFastBounds(const SkRect& bounds) const {
   *     // See onAffectsTransparentBlack(), but without knowing the local-to-device transform, we don't
   *     // know how many pixels will be sampled by the kernel. Return unbounded to match the
   *     // expectations of an image filter that "affects" transparent black.
   *     return SkRectPriv::MakeLargeS32();
   * }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrixConvolutionImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeInt(fKernelSize.width());
   *     buffer.writeInt(fKernelSize.height());
   *     buffer.writeScalarArray(fKernel);
   *     buffer.writeScalar(fGain);
   *     buffer.writeScalar(fBias);
   *     buffer.writeInt(fKernelOffset.x());
   *     buffer.writeInt(fKernelOffset.y());
   *     buffer.writeBool(fConvolveAlpha);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAffectsTransparentBlack() const override {
   *         // affectsTransparentBlack() is conflated with "canComputeFastBounds" and MatrixConvolution
   *         // is unique in that it might not produce unbounded output, but we can't calculate the
   *         // fast bounds because the kernel is applied in device space and no transform is provided
   *         // with that API.
   *         // TODO(skbug.com/40045519): Accept a matrix in computeFastBounds() so that we can handle the
   *         // layer-space kernel case.
   *
   *         // That issue aside, a matrix convolution can affect transparent black when it has a
   *         // non-zero bias and convolves alpha (if it doesn't convolve the alpha channel then the bias
   *         // applied to RGB doesn't matter for transparent black pixels).
   *         // NOTE: The crop image filters that wrap the matrix convolution to apply tile modes will
   *         // reset this property when possible.
   *         return true;
   *     }
   * ```
   */
  public override fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkMatrixConvolutionImageFilter::onFilterImage(
   *         const skif::Context& context) const {
   *     using ShaderFlags = skif::FilterResult::ShaderFlags;
   *
   *     skif::LayerSpace<SkIRect> requiredInput = this->boundsSampledByKernel(context.desiredOutput());
   *     skif::FilterResult childOutput =
   *             this->getChildOutput(0, context.withNewDesiredOutput(requiredInput));
   *
   *     skif::LayerSpace<SkIRect> outputBounds;
   *     if (fConvolveAlpha && fBias != 0.f) {
   *         // The convolution will produce a non-trivial value for every pixel so fill desired output.
   *         outputBounds = context.desiredOutput();
   *     } else {
   *         // Calculate the possible extent of the convolution given what was actually produced by the
   *         // child filter and then intersect that with the desired output.
   *         outputBounds = this->boundsAffectedByKernel(childOutput.layerBounds());
   *         if (!outputBounds.intersect(context.desiredOutput())) {
   *             return {};
   *         }
   *     }
   *
   *     skif::FilterResult::Builder builder{context};
   *     builder.add(childOutput,
   *                 this->boundsSampledByKernel(outputBounds),
   *                 ShaderFlags::kSampledRepeatedly);
   *     return builder.eval([&](SkSpan<sk_sp<SkShader>> inputs) {
   *         return this->createShader(context, inputs[0]);
   *     }, outputBounds);
   * }
   * ```
   */
  public override fun onFilterImage(context: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMatrixConvolutionImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // Adjust the desired output bounds by the kernel size to avoid evaluating edge conditions, and
   *     // then recurse to the child filter.
   *     skif::LayerSpace<SkIRect> requiredInput = this->boundsSampledByKernel(desiredOutput);
   *     return this->getChildInputLayerBounds(0, mapping, requiredInput, contentBounds);
   * }
   * ```
   */
  public override fun onGetInputLayerBounds(
    mapping: Mapping,
    desiredOutput: LayerSpace<SkIRect>,
    contentBounds: LayerSpace<SkIRect>?,
  ): LayerSpace<SkIRect> {
    TODO("Implement onGetInputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skif::LayerSpace<SkIRect>> SkMatrixConvolutionImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     if (fConvolveAlpha && fBias != 0.f) {
   *         // Applying the kernel as a convolution to fully transparent black will result in 0 for
   *         // each channel, unless the bias itself shifts this "zero-point". However, when the alpha
   *         // channel is not convolved, the original a=0 is preserved and producing a premul color
   *         // discards the non-zero bias. Convolving the alpha channel and a non-zero bias can mean
   *         // the transparent black pixels outside of any input image become non-transparent black.
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     }
   *
   *     // Otherwise apply the kernel to the output bounds of the child filter.
   *     auto outputBounds = this->getChildOutputLayerBounds(0, mapping, contentBounds);
   *     if (outputBounds) {
   *         return this->boundsAffectedByKernel(*outputBounds);
   *     } else {
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     }
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMatrixConvolutionImageFilter::boundsSampledByKernel(
   *         const skif::LayerSpace<SkIRect>& bounds) const {
   *     return adjust(bounds,
   *                   -fKernelOffset.x(),
   *                   -fKernelOffset.y(),
   *                   fKernelSize.width() - fKernelOffset.x() - 1,
   *                   fKernelSize.height() - fKernelOffset.y() - 1);
   * }
   * ```
   */
  private fun boundsSampledByKernel(bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement boundsSampledByKernel")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMatrixConvolutionImageFilter::boundsAffectedByKernel(
   *         const skif::LayerSpace<SkIRect>& bounds) const {
   *     return adjust(bounds,
   *                   fKernelOffset.x() - fKernelSize.width() + 1,
   *                   fKernelOffset.y() - fKernelSize.height() + 1,
   *                   fKernelOffset.x(),
   *                   fKernelOffset.y());
   * }
   * ```
   */
  private fun boundsAffectedByKernel(bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement boundsAffectedByKernel")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkMatrixConvolutionImageFilter::createShader(const skif::Context& ctx,
   *                                                              sk_sp<SkShader> input) const {
   *     const int kernelLength = fKernelSize.width() * fKernelSize.height();
   *     auto [_, key] = quantize_by_kernel_size(kernelLength);
   *     const bool useTextureShader = (key != SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms);
   *     if (useTextureShader && fKernelBitmap.empty()) {
   *         return nullptr; // No actual kernel data to work with from a prior OOM
   *     }
   *
   *     const SkRuntimeEffect* matrixConvEffect = GetKnownRuntimeEffect(key);
   *
   *     SkRuntimeShaderBuilder builder(sk_ref_sp(matrixConvEffect));
   *     builder.child("child") = std::move(input);
   *
   *     if (useTextureShader) {
   *         sk_sp<SkImage> cachedKernel = ctx.backend()->getCachedBitmap(fKernelBitmap);
   *         if (!cachedKernel) {
   *             return nullptr;
   *         }
   *         builder.child("kernel") = cachedKernel->makeRawShader(SkFilterMode::kNearest);
   *         builder.uniform("innerGainAndBias") = SkV2{fInnerGain, fInnerBias};
   *     } else {
   *         float paddedKernel[kMaxUniformKernelSize];
   *         memcpy(paddedKernel, fKernel.data(), kernelLength*sizeof(float));
   *         memset(paddedKernel+kernelLength, 0, (kMaxUniformKernelSize - kernelLength)*sizeof(float));
   *
   *         builder.uniform("kernel").set(paddedKernel, kMaxUniformKernelSize);
   *     }
   *
   *     builder.uniform("size") = SkISize(fKernelSize);
   *     builder.uniform("offset") = skif::IVector(fKernelOffset);
   *     // Scale the user-provided bias by 1/255 to match the [0,1] color channel range
   *     builder.uniform("gainAndBias") = SkV2{fGain, fBias / 255.f};
   *     builder.uniform("convolveAlpha") = fConvolveAlpha ? 1 : 0;
   *
   *     return builder.makeShader();
   * }
   * ```
   */
  private fun createShader(ctx: Context, input: SkSp<SkShader>): SkSp<SkShader> {
    TODO("Implement createShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkMatrixConvolutionImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *
   *     SkISize kernelSize;
   *     kernelSize.fWidth = buffer.readInt();
   *     kernelSize.fHeight = buffer.readInt();
   *     const int count = buffer.getArrayCount();
   *
   *     const int64_t kernelArea = sk_64_mul(kernelSize.width(), kernelSize.height());
   *     if (!buffer.validate(kernelArea == count)) {
   *         return nullptr;
   *     }
   *     if (!buffer.validateCanReadN<SkScalar>(count)) {
   *         return nullptr;
   *     }
   *     AutoSTArray<16, SkScalar> kernel(count);
   *     if (!buffer.readScalarArray(kernel)) {
   *         return nullptr;
   *     }
   *     SkScalar gain = buffer.readScalar();
   *     SkScalar bias = buffer.readScalar();
   *     SkIPoint kernelOffset;
   *     kernelOffset.fX = buffer.readInt();
   *     kernelOffset.fY = buffer.readInt();
   *
   *     SkTileMode tileMode = SkTileMode::kDecal;
   *     if (buffer.isVersionLT(SkPicturePriv::kConvolutionImageFilterTilingUpdate)) {
   *         tileMode = buffer.read32LE(SkTileMode::kLastTileMode);
   *     } // else SkCropImageFilter handles the tile mode (if any)
   *
   *     bool convolveAlpha = buffer.readBool();
   *
   *     if (!buffer.isValid()) {
   *         return nullptr;
   *     }
   *     // NOTE: For SKPs with version >= kConvolutionImageFilterTilingUpdate, tileMode will be kDecal
   *     // and common.cropRect() will be null (so the factory also ignores tileMode). Any
   *     // cropping/tiling will have been handled by the deserialized input/output Crop image filters.
   *     return SkImageFilters::MatrixConvolution(
   *                 kernelSize, kernel.get(), gain, bias, kernelOffset, tileMode,
   *                 convolveAlpha, common.getInput(0), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
