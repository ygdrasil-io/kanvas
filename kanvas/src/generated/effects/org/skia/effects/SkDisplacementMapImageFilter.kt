package org.skia.effects

import kotlin.Array
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkColorChannel
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkDisplacementMapImageFilter final : public SkImageFilter_Base {
 *     // Input image filter indices
 *     static constexpr int kDisplacement = 0;
 *     static constexpr int kColor = 1;
 *
 *     // TODO(skbug.com/40045448): Use nearest to match historical behavior, but eventually this should
 *     // become a factory option.
 *     static constexpr SkSamplingOptions kDisplacementSampling{SkFilterMode::kNearest};
 *
 * public:
 *     SkDisplacementMapImageFilter(SkColorChannel xChannel,
 *                                  SkColorChannel yChannel,
 *                                  SkScalar scale,
 *                                  sk_sp<SkImageFilter> inputs[2])
 *             : SkImageFilter_Base(inputs, 2)
 *             , fXChannel(xChannel)
 *             , fYChannel(yChannel)
 *             , fScale(scale) {
 *         SkASSERT(SkIsFinite(fScale));
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& src) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterDisplacementMapImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkDisplacementMapImageFilter)
 *
 *     skif::FilterResult onFilterImage(const skif::Context&) const override;
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
 *     skif::LayerSpace<SkIRect> outsetByMaxDisplacement(const skif::Mapping& mapping,
 *                                                       skif::LayerSpace<SkIRect> bounds) const {
 *         // For max displacement, we treat 'scale' as a size instead of a vector. The vector offset
 *         // maps a [0,1] channel value to [-scale/2, scale/2], and treating it as a size
 *         // automatically accounts for the absolute magnitude when transforming from param to layer.
 *         skif::LayerSpace<SkSize> maxDisplacement = mapping.paramToLayer(
 *             skif::ParameterSpace<SkSize>({0.5f * fScale, 0.5f * fScale}));
 *         bounds.outset(maxDisplacement.ceil());
 *         return bounds;
 *     }
 *
 *     SkColorChannel fXChannel;
 *     SkColorChannel fYChannel;
 *     // Scale is really a ParameterSpace<Vector> where width = height = fScale, but we store just the
 *     // float here for easier serialization and convert to a size in onFilterImage().
 *     SkScalar fScale;
 * }
 * ```
 */
public class SkDisplacementMapImageFilter public constructor(
  xChannel: SkColorChannel,
  yChannel: SkColorChannel,
  scale: SkScalar,
  inputs: Array<SkSp<SkImageFilter>>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kDisplacement = 0
   * ```
   */
  private var fXChannel: SkColorChannel = TODO("Initialize fXChannel")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kColor = 1
   * ```
   */
  private var fYChannel: SkColorChannel = TODO("Initialize fYChannel")

  /**
   * C++ original:
   * ```cpp
   * static constexpr SkSamplingOptions kDisplacementSampling{SkFilterMode::kNearest}
   * ```
   */
  private var fScale: SkScalar = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkDisplacementMapImageFilter::computeFastBounds(const SkRect& src) const {
   *     SkRect colorBounds = this->getInput(kColor) ? this->getInput(kColor)->computeFastBounds(src)
   *                                                 : src;
   *     float maxDisplacement = 0.5f * SkScalarAbs(fScale);
   *     return colorBounds.makeOutset(maxDisplacement, maxDisplacement);
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDisplacementMapImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeInt((int) fXChannel);
   *     buffer.writeInt((int) fYChannel);
   *     buffer.writeScalar(fScale);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkDisplacementMapImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     skif::LayerSpace<SkIRect> requiredColorInput =
   *             this->outsetByMaxDisplacement(ctx.mapping(), ctx.desiredOutput());
   *     skif::FilterResult colorOutput =
   *             this->getChildOutput(kColor, ctx.withNewDesiredOutput(requiredColorInput));
   *     if (!colorOutput) {
   *         return {}; // No non-transparent black colors to displace
   *     }
   *
   *     // When the color image filter is unrestricted, its output will be 'maxDisplacement' larger than
   *     // this filter's desired output. However, if it is cropped, we can restrict this filter's final
   *     // output. However it's not simply colorOutput intersected with desiredOutput since we have to
   *     // account for how the clipped colorOutput might still be displaced.
   *     skif::LayerSpace<SkIRect> outputBounds =
   *             this->outsetByMaxDisplacement(ctx.mapping(), colorOutput.layerBounds());
   *     // 'outputBounds' has double the max displacement for edges where colorOutput had not been
   *     // clipped, but that's fine since we intersect with 'desiredOutput'. For edges that were cropped
   *     // the second max displacement represents how far they can be displaced, which might be inside
   *     // the original 'desiredOutput'.
   *     if (!outputBounds.intersect(ctx.desiredOutput())) {
   *         // None of the non-transparent black colors can be displaced into the desired bounds.
   *         return {};
   *     }
   *
   *     // Creation of the displacement map should happen in a non-colorspace aware context. This
   *     // texture is a purely mathematical construct, so we want to just operate on the stored
   *     // values. Consider:
   *     //
   *     //   User supplies an sRGB displacement map. If we're rendering to a wider gamut, then we could
   *     //   end up filtering the displacement map into that gamut, which has the effect of reducing
   *     //   the amount of displacement that it represents (as encoded values move away from the
   *     //   primaries).
   *     //
   *     //   With a more complex DAG attached to this input, it's not clear that working in ANY specific
   *     //   color space makes sense, so we ignore color spaces (and gamma) entirely. This may not be
   *     //   ideal, but it's at least consistent and predictable.
   *     skif::FilterResult displacementOutput =
   *             this->getChildOutput(kDisplacement, ctx.withNewDesiredOutput(outputBounds)
   *                                                    .withNewColorSpace(/*cs=*/nullptr));
   *
   *     // NOTE: The scale is a "vector" not a "size" since we want to preserve negations on the final
   *     // displacement vector.
   *     const skif::LayerSpace<skif::Vector> scale =
   *             ctx.mapping().paramToLayer(skif::ParameterSpace<skif::Vector>({fScale, fScale}));
   *     if (!displacementOutput) {
   *         // A null displacement map means its transparent black, but (0,0,0,0) becomes the vector
   *         // (-scale/2, -scale/2) applied to the color image, so represent the displacement as a
   *         // simple transform.
   *         skif::LayerSpace<SkMatrix> constantDisplacement{SkMatrix::Translate(-0.5f * scale.x(),
   *                                                                             -0.5f * scale.y())};
   *         return colorOutput.applyTransform(ctx, constantDisplacement, kDisplacementSampling);
   *     }
   *
   *     // If we made it this far, then we actually have per-pixel displacement affecting the color
   *     // image. We need to evaluate each pixel within 'outputBounds'.
   *     using ShaderFlags = skif::FilterResult::ShaderFlags;
   *
   *     skif::FilterResult::Builder builder{ctx};
   *     builder.add(displacementOutput, /*sampleBounds=*/outputBounds);
   *     builder.add(colorOutput,
   *                 /*sampleBounds=*/requiredColorInput,
   *                 ShaderFlags::kNonTrivialSampling,
   *                 kDisplacementSampling);
   *     return builder.eval(
   *             [&](SkSpan<sk_sp<SkShader>> inputs) {
   *                 return make_displacement_shader(inputs[kDisplacement], inputs[kColor],
   *                                                 scale, fXChannel, fYChannel);
   *             }, outputBounds);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkDisplacementMapImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // Pixels up to the maximum displacement away from 'desiredOutput' can be moved into those
   *     // bounds, depending on how the displacement map renders. To ensure those colors are defined,
   *     // we require that outset buffer around 'desiredOutput' from the color map.
   *     skif::LayerSpace<SkIRect> requiredInput = this->outsetByMaxDisplacement(mapping, desiredOutput);
   *     requiredInput = this->getChildInputLayerBounds(kColor, mapping, requiredInput, contentBounds);
   *
   *     // Accumulate the required input for the displacement filter to cover the original desired out
   *     requiredInput.join(this->getChildInputLayerBounds(
   *             kDisplacement, mapping, desiredOutput, contentBounds));
   *     return requiredInput;
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
   * std::optional<skif::LayerSpace<SkIRect>> SkDisplacementMapImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     auto colorOutput = this->getChildOutputLayerBounds(kColor, mapping, contentBounds);
   *     if (colorOutput) {
   *         return this->outsetByMaxDisplacement(mapping, *colorOutput);
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
   * skif::LayerSpace<SkIRect> outsetByMaxDisplacement(const skif::Mapping& mapping,
   *                                                       skif::LayerSpace<SkIRect> bounds) const {
   *         // For max displacement, we treat 'scale' as a size instead of a vector. The vector offset
   *         // maps a [0,1] channel value to [-scale/2, scale/2], and treating it as a size
   *         // automatically accounts for the absolute magnitude when transforming from param to layer.
   *         skif::LayerSpace<SkSize> maxDisplacement = mapping.paramToLayer(
   *             skif::ParameterSpace<SkSize>({0.5f * fScale, 0.5f * fScale}));
   *         bounds.outset(maxDisplacement.ceil());
   *         return bounds;
   *     }
   * ```
   */
  private fun outsetByMaxDisplacement(mapping: Mapping, bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement outsetByMaxDisplacement")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkDisplacementMapImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 2);
   *
   *     SkColorChannel xsel = buffer.read32LE(SkColorChannel::kLastEnum);
   *     SkColorChannel ysel = buffer.read32LE(SkColorChannel::kLastEnum);
   *     SkScalar      scale = buffer.readScalar();
   *
   *     return SkImageFilters::DisplacementMap(xsel, ysel, scale, common.getInput(0),
   *                                            common.getInput(1), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    private val kDisplacement: Int = TODO("Initialize kDisplacement")

    private val kColor: Int = TODO("Initialize kColor")

    private val kDisplacementSampling: SkSamplingOptions = TODO("Initialize kDisplacementSampling")
  }
}
