package org.skia.effects

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.SkImageFilterBase
import org.skia.core.SkSpinlock
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkRuntimeImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkRuntimeImageFilter(const SkRuntimeShaderBuilder& builder,
 *                          float maxSampleRadius,
 *                          std::string_view childShaderNames[],
 *                          const sk_sp<SkImageFilter> inputs[],
 *                          int inputCount)
 *             : SkImageFilter_Base(inputs, inputCount)
 *             , fRuntimeEffectBuilder(builder)
 *             , fMaxSampleRadius(maxSampleRadius) {
 *         SkASSERT(maxSampleRadius >= 0.f);
 *         fChildShaderNames.reserve_exact(inputCount);
 *         for (int i = 0; i < inputCount; i++) {
 *             fChildShaderNames.push_back(SkString(childShaderNames[i]));
 *         }
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& src) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterRuntimeImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkRuntimeImageFilter)
 *
 *     bool onAffectsTransparentBlack() const override { return true; }
 *     // Currently there is no way for a client to specify the semantics of geometric uniforms that
 *     // should respond to the canvas matrix. Forcing translate-only is a hammer that lets the output
 *     // be correct at the expense of resolution when there's a lot of scaling. See skbug.com/40044507.
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kTranslate; }
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
 *     skif::LayerSpace<SkIRect> applyMaxSampleRadius(
 *             const skif::Mapping& mapping,
 *             skif::LayerSpace<SkIRect> bounds) const {
 *         skif::LayerSpace<SkISize> maxSampleRadius = mapping.paramToLayer(
 *                 skif::ParameterSpace<SkSize>({fMaxSampleRadius, fMaxSampleRadius})).ceil();
 *         bounds.outset(maxSampleRadius);
 *         return bounds;
 *     }
 *
 *     mutable SkSpinlock fRuntimeEffectLock;
 *     mutable SkRuntimeShaderBuilder fRuntimeEffectBuilder;
 *     STArray<1, SkString> fChildShaderNames;
 *     float fMaxSampleRadius;
 * }
 * ```
 */
public class SkRuntimeImageFilter public constructor(
  builder: SkRuntimeShaderBuilder,
  maxSampleRadius: Float,
  childShaderNames: Array<String>,
  inputs: Array<SkSp<SkImageFilter>>,
  inputCount: Int,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fRuntimeEffectLock
   * ```
   */
  private var fRuntimeEffectLock: SkSpinlock = TODO("Initialize fRuntimeEffectLock")

  /**
   * C++ original:
   * ```cpp
   * mutable SkRuntimeShaderBuilder fRuntimeEffectBuilder
   * ```
   */
  private var fRuntimeEffectBuilder: SkRuntimeShaderBuilder =
      TODO("Initialize fRuntimeEffectBuilder")

  /**
   * C++ original:
   * ```cpp
   * STArray<1, SkString> fChildShaderNames
   * ```
   */
  private var fChildShaderNames: Int = TODO("Initialize fChildShaderNames")

  /**
   * C++ original:
   * ```cpp
   * float fMaxSampleRadius
   * ```
   */
  private var fMaxSampleRadius: Float = TODO("Initialize fMaxSampleRadius")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkRuntimeImageFilter::computeFastBounds(const SkRect& src) const {
   *     // Can't predict what the RT Shader will generate (see onGetOutputLayerBounds)
   *     return SkRectPriv::MakeLargeS32();
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRuntimeImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     fRuntimeEffectLock.acquire();
   *     buffer.writeString(fRuntimeEffectBuilder.effect()->source().c_str());
   *     buffer.writeDataAsByteArray(fRuntimeEffectBuilder.uniforms().get());
   *     for (const SkString& name : fChildShaderNames) {
   *         buffer.writeString(name.c_str());
   *     }
   *     for (size_t x = 0; x < fRuntimeEffectBuilder.children().size(); x++) {
   *         buffer.writeFlattenable(fRuntimeEffectBuilder.children()[x].flattenable());
   *     }
   *     fRuntimeEffectLock.release();
   *
   *     buffer.writeScalar(fMaxSampleRadius);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAffectsTransparentBlack() const override { return true; }
   * ```
   */
  public override fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kTranslate; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkRuntimeImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     using ShaderFlags = skif::FilterResult::ShaderFlags;
   *
   *     const int inputCount = this->countInputs();
   *     SkASSERT(inputCount == fChildShaderNames.size());
   *
   *     skif::Context inputCtx = ctx.withNewDesiredOutput(
   *             this->applyMaxSampleRadius(ctx.mapping(), ctx.desiredOutput()));
   *     skif::FilterResult::Builder builder{ctx};
   *     for (int i = 0; i < inputCount; ++i) {
   *         // Record the input context's desired output as the sample bounds for the child shaders
   *         // since the runtime shader can go up to max sample radius away from its desired output
   *         // (which is the default sample bounds if we didn't override it here).
   *         builder.add(this->getChildOutput(i, inputCtx),
   *                     inputCtx.desiredOutput(),
   *                     ShaderFlags::kNonTrivialSampling);
   *     }
   *     return builder.eval([&](SkSpan<sk_sp<SkShader>> inputs) {
   *         // lock the mutation of the builder and creation of the shader so that the builder's state
   *         // is const and is safe for multi-threaded access.
   *         fRuntimeEffectLock.acquire();
   *         for (int i = 0; i < inputCount; i++) {
   *             fRuntimeEffectBuilder.child(fChildShaderNames[i].c_str()) = inputs[i];
   *         }
   *         sk_sp<SkShader> shader = fRuntimeEffectBuilder.makeShader();
   *
   *         // Remove the inputs from the builder to avoid unnecessarily prolonging the input shaders'
   *         // lifetimes.
   *         for (int i = 0; i < inputCount; i++) {
   *             fRuntimeEffectBuilder.child(fChildShaderNames[i].c_str()) = nullptr;
   *         }
   *         fRuntimeEffectLock.release();
   *
   *         return shader;
   *     }, {}, /*evaluateInParameterSpace=*/true);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkRuntimeImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     const int inputCount = this->countInputs();
   *     if (inputCount <= 0) {
   *         return skif::LayerSpace<SkIRect>::Empty();
   *     } else {
   *         // Provide 'maxSampleRadius' pixels (in layer space) to the child shaders.
   *         skif::LayerSpace<SkIRect> requiredInput =
   *                 this->applyMaxSampleRadius(mapping, desiredOutput);
   *
   *         // Union of all child input bounds so that one source image can provide for all of them.
   *         return skif::LayerSpace<SkIRect>::Union(
   *                 inputCount,
   *                 [&](int i) {
   *                     return this->getChildInputLayerBounds(i, mapping, requiredInput, contentBounds);
   *                 });
   *     }
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
   * std::optional<skif::LayerSpace<SkIRect>> SkRuntimeImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& /*mapping*/,
   *         std::optional<skif::LayerSpace<SkIRect>> /*contentBounds*/) const {
   *     // Pessimistically assume it can cover anything
   *     return skif::LayerSpace<SkIRect>::Unbounded();
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> applyMaxSampleRadius(
   *             const skif::Mapping& mapping,
   *             skif::LayerSpace<SkIRect> bounds) const {
   *         skif::LayerSpace<SkISize> maxSampleRadius = mapping.paramToLayer(
   *                 skif::ParameterSpace<SkSize>({fMaxSampleRadius, fMaxSampleRadius})).ceil();
   *         bounds.outset(maxSampleRadius);
   *         return bounds;
   *     }
   * ```
   */
  private fun applyMaxSampleRadius(mapping: Mapping, bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement applyMaxSampleRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkRuntimeImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     // We don't know how many inputs to expect yet. Passing -1 allows any number of children.
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, -1);
   *     if (common.cropRect()) {
   *         return nullptr;
   *     }
   *
   *     // Read the SkSL string and convert it into a runtime effect
   *     SkString sksl;
   *     buffer.readString(&sksl);
   *     auto effect = SkMakeCachedRuntimeEffect(SkRuntimeEffect::MakeForShader, std::move(sksl));
   *     if (!buffer.validate(effect != nullptr)) {
   *         return nullptr;
   *     }
   *
   *     // Read the uniform data and make sure it matches the size from the runtime effect
   *     sk_sp<SkData> uniforms = buffer.readByteArrayAsData();
   *     if (!buffer.validate(uniforms->size() == effect->uniformSize())) {
   *         return nullptr;
   *     }
   *
   *     // Read the child shader names
   *     STArray<4, std::string_view> childShaderNames;
   *     STArray<4, SkString> childShaderNameStrings;
   *     childShaderNames.resize(common.inputCount());
   *     childShaderNameStrings.resize(common.inputCount());
   *     for (int i = 0; i < common.inputCount(); i++) {
   *         buffer.readString(&childShaderNameStrings[i]);
   *         childShaderNames[i] = childShaderNameStrings[i].c_str();
   *     }
   *
   *     SkRuntimeShaderBuilder builder(std::move(effect), std::move(uniforms));
   *
   *     // Populate the builder with the corresponding children
   *     for (const SkRuntimeEffect::Child& child : builder.effect()->children()) {
   *         std::string_view name = child.name;
   *         switch (child.type) {
   *             case SkRuntimeEffect::ChildType::kBlender: {
   *                 builder.child(name) = buffer.readBlender();
   *                 break;
   *             }
   *             case SkRuntimeEffect::ChildType::kColorFilter: {
   *                 builder.child(name) = buffer.readColorFilter();
   *                 break;
   *             }
   *             case SkRuntimeEffect::ChildType::kShader: {
   *                 builder.child(name) = buffer.readShader();
   *                 break;
   *             }
   *         }
   *     }
   *
   *     float maxSampleRadius = 0.f; // default before sampleRadius was exposed in the factory
   *     if (!buffer.isVersionLT(SkPicturePriv::kRuntimeImageFilterSampleRadius)) {
   *         maxSampleRadius = buffer.readScalar();
   *     }
   *
   *     if (!buffer.isValid()) {
   *         return nullptr;
   *     }
   *
   *     return SkImageFilters::RuntimeShader(builder, maxSampleRadius, childShaderNames.data(),
   *                                          common.inputs(), common.inputCount());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
