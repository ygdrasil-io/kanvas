package org.skia.effects

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkBlender
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkV4

/**
 * C++ original:
 * ```cpp
 * class SkBlendImageFilter : public SkImageFilter_Base {
 *     // Input image filter indices
 *     static constexpr int kBackground = 0;
 *     static constexpr int kForeground = 1;
 *
 * public:
 *     SkBlendImageFilter(sk_sp<SkBlender> blender,
 *                        const std::optional<SkV4>& coefficients,
 *                        bool enforcePremul,
 *                        sk_sp<SkImageFilter> inputs[2])
 *             : SkImageFilter_Base(inputs, 2)
 *             , fBlender(std::move(blender))
 *             , fArithmeticCoefficients(coefficients)
 *             , fEnforcePremul(enforcePremul) {
 *         // A null blender represents src-over, which should have been filled in by the factory
 *         SkASSERT(fBlender);
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& bounds) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     static constexpr uint32_t kArithmetic_SkBlendMode = kCustom_SkBlendMode + 1;
 *
 *     friend void ::SkRegisterBlendImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkBlendImageFilter)
 *     static sk_sp<SkFlattenable> LegacyArithmeticCreateProc(SkReadBuffer& buffer);
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
 *
 *     bool onAffectsTransparentBlack() const override {
 *         // An arbitrary runtime blender or an arithmetic runtime blender with k3 != 0 affects
 *         // transparent black.
 *         return !as_BB(fBlender)->asBlendMode().has_value() &&
 *                (!fArithmeticCoefficients.has_value() || (*fArithmeticCoefficients)[3] != 0.f);
 *     }
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
 *     sk_sp<SkShader> makeBlendShader(sk_sp<SkShader> bg, sk_sp<SkShader> fg) const;
 *
 *     sk_sp<SkBlender> fBlender;
 *
 *     // Normally runtime SkBlenders are pessimistic about the bounds they affect. For Arithmetic,
 *     // we remember the coefficients so that bounds can be reasoned about.
 *     std::optional<SkV4> fArithmeticCoefficients;
 *     bool fEnforcePremul; // Remembered to serialize the Arithmetic variant correctly
 * }
 * ```
 */
public open class SkBlendImageFilter public constructor(
  blender: SkSp<SkBlender>,
  coefficients: SkV4?,
  enforcePremul: Boolean,
  inputs: Array<SkSp<SkImageFilter>>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kBackground = 0
   * ```
   */
  private var fBlender: SkSp<SkBlender> = TODO("Initialize fBlender")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kForeground = 1
   * ```
   */
  private var fArithmeticCoefficients: Int = TODO("Initialize fArithmeticCoefficients")

  /**
   * C++ original:
   * ```cpp
   * static constexpr uint32_t kArithmetic_SkBlendMode = kCustom_SkBlendMode + 1
   * ```
   */
  private var fEnforcePremul: Boolean = TODO("Initialize fEnforcePremul")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkBlendImageFilter::computeFastBounds(const SkRect& bounds) const {
   *     // TODO: This is a prime example of why computeFastBounds() and onGetOutputLayerBounds() should
   *     // be combined into the same function.
   *     bool transparentOutsideFG = false;
   *     bool transparentOutsideBG = false;
   *     if (auto bm = as_BB(fBlender)->asBlendMode()) {
   *         SkASSERT(*bm != SkBlendMode::kClear); // Should have been caught at creation time
   *         SkBlendModeCoeff src, dst;
   *         if (SkBlendMode_AsCoeff(*bm, &src, &dst)) {
   *             // If dst's coefficient is 0 then nothing can produce non-transparent content outside
   *             // of the foreground. When dst coefficient is SA, it will always be 0 outside the FG.
   *             transparentOutsideFG = dst == SkBlendModeCoeff::kZero || dst == SkBlendModeCoeff::kSA;
   *             // And the reverse is true for src and the background content.
   *             transparentOutsideBG = src == SkBlendModeCoeff::kZero || src == SkBlendModeCoeff::kDA;
   *         }
   *     } else if (fArithmeticCoefficients.has_value()) {
   *         [[maybe_unused]] static constexpr SkV4 kClearCoeff = {0.f, 0.f, 0.f, 0.f};
   *         const SkV4& k = *fArithmeticCoefficients;
   *         SkASSERT(k != kClearCoeff); // Should have been converted to an empty image filter
   *
   *         if (k[3] != 0.f) {
   *             // The arithmetic equation produces non-transparent black everywhere
   *             return SkRectPriv::MakeLargeS32();
   *         } else {
   *             // Given the earlier assert and if, then (k[1] == k[2] == 0) implies k[0] != 0. If only
   *             // one of k[1] or k[2] are non-zero then, regardless of k[0], then only that bounds
   *             // has non-transparent content.
   *             transparentOutsideFG = k[2] == 0.f;
   *             transparentOutsideBG = k[1] == 0.f;
   *         }
   *     } else {
   *         // A non-arithmetic runtime blender, so pessimistically assume it can return non-transparent
   *         // black anywhere.
   *         return SkRectPriv::MakeLargeS32();
   *     }
   *
   *     SkRect foregroundBounds = this->getInput(kForeground) ?
   *             this->getInput(kForeground)->computeFastBounds(bounds) : bounds;
   *     SkRect backgroundBounds = this->getInput(kBackground) ?
   *             this->getInput(kBackground)->computeFastBounds(bounds) : bounds;
   *     if (transparentOutsideFG) {
   *         if (transparentOutsideBG) {
   *             // Output is the intersection of both
   *             if (!foregroundBounds.intersect(backgroundBounds)) {
   *                 return SkRect::MakeEmpty();
   *             }
   *         }
   *         return foregroundBounds;
   *     } else {
   *         if (!transparentOutsideBG) {
   *             // Output is the union of both (infinite bounds were detected earlier).
   *             backgroundBounds.join(foregroundBounds);
   *         }
   *         return backgroundBounds;
   *     }
   * }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlendImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     if (fArithmeticCoefficients.has_value()) {
   *         buffer.write32(kArithmetic_SkBlendMode);
   *
   *         const SkV4& k = *fArithmeticCoefficients;
   *         buffer.writeScalar(k[0]);
   *         buffer.writeScalar(k[1]);
   *         buffer.writeScalar(k[2]);
   *         buffer.writeScalar(k[3]);
   *         buffer.writeBool(fEnforcePremul);
   *     } else if (auto bm = as_BB(fBlender)->asBlendMode()) {
   *         buffer.write32((unsigned)bm.value());
   *     } else {
   *         buffer.write32(kCustom_SkBlendMode);
   *         buffer.writeFlattenable(fBlender.get());
   *     }
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAffectsTransparentBlack() const override {
   *         // An arbitrary runtime blender or an arithmetic runtime blender with k3 != 0 affects
   *         // transparent black.
   *         return !as_BB(fBlender)->asBlendMode().has_value() &&
   *                (!fArithmeticCoefficients.has_value() || (*fArithmeticCoefficients)[3] != 0.f);
   *     }
   * ```
   */
  public override fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkBlendImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     // We could just request 'desiredOutput' for the blend's required input size, since that's what
   *     // it is expected to fill. However, some blend modes restrict the output to something other
   *     // than the union of the foreground and background. To make this restriction available to both
   *     // children before evaluating them, we determine the maximum possible output the blend can
   *     // produce from the contentBounds and require that for both children to produce.
   *     auto requiredInput = this->onGetOutputLayerBounds(ctx.mapping(), ctx.source().layerBounds());
   *     if (requiredInput) {
   *         if (!requiredInput->intersect(ctx.desiredOutput())) {
   *             return {};
   *         }
   *     } else {
   *         requiredInput = ctx.desiredOutput();
   *     }
   *
   *     skif::Context inputCtx = ctx.withNewDesiredOutput(*requiredInput);
   *     skif::FilterResult::Builder builder{ctx};
   *     builder.add(this->getChildOutput(kBackground, inputCtx));
   *     builder.add(this->getChildOutput(kForeground, inputCtx));
   *     return builder.eval(
   *             [&](SkSpan<sk_sp<SkShader>> inputs) -> sk_sp<SkShader> {
   *                 return this->makeBlendShader(inputs[kBackground], inputs[kForeground]);
   *             }, requiredInput);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkBlendImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *
   *     skif::LayerSpace<SkIRect> requiredInput;
   *     std::optional<skif::LayerSpace<SkIRect>> maxOutput;
   *     if (contentBounds && (maxOutput = this->onGetOutputLayerBounds(mapping, *contentBounds))) {
   *         // See comment in onFilterImage().
   *         requiredInput = *maxOutput;
   *         if (!requiredInput.intersect(desiredOutput)) {
   *             // Don't bother recursing if we know the blend will discard everything
   *             return skif::LayerSpace<SkIRect>::Empty();
   *         }
   *     } else {
   *         // The content and/or the output of the child are unbounded so the intersection with the
   *         // desired output is simply the desired output.
   *         requiredInput = desiredOutput;
   *     }
   *
   *     // Return the union of both FG and BG required inputs to ensure both have all necessary pixels
   *     skif::LayerSpace<SkIRect> bgInput =
   *             this->getChildInputLayerBounds(kBackground, mapping, requiredInput, contentBounds);
   *     skif::LayerSpace<SkIRect> fgInput =
   *             this->getChildInputLayerBounds(kForeground, mapping, requiredInput, contentBounds);
   *
   *     bgInput.join(fgInput);
   *     return bgInput;
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
   * std::optional<skif::LayerSpace<SkIRect>> SkBlendImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // Blending is (k0*FG*BG +       k1*FG +       k2*BG + k3) for arithmetic blenders OR
   *     //             ( 0*FG*BG + srcCoeff*FG + dstCoeff*BG + 0 ) for Porter-Duff blend modes OR
   *     //              un-inspectable(FG, BG) for advanced blend modes and other runtime blenders.
   *     //
   *     // There are six possible output bounds that can be produced:
   *     //   1. No output: K = (0,0,0,0) or (srcCoeff,dstCoeff) = (kZero,kZero)
   *     //   2. intersect(FG,BG): K = (non-zero, 0,0,0) or (srcCoeff,dstCoeff) = (kZero|kDA, kZero|kSA)
   *     //   3. FG-only: K = (0, non-zero, 0,0) or (srcCoeff,dstCoeff) = (!kZero&!kDA, kZero|kSA)
   *     //   4. BG-only: K = (0,0, non-zero, 0) or (srcCoeff,dstCoeff) = (kZero|kDA, !kZero&!kSA)
   *     //   5. union(FG,BG): K = (*,*,*,0) or (srcCoeff,dstCoeff) = (!kZero&!kDA, !kZero&!kSA)
   *     //        or an advanced blend mode.
   *     //   6. infinite: K = (*,*,*, non-zero) or a runtime blender other than SkBlenders::Arithmetic.
   *     bool transparentOutsideFG = false;
   *     bool transparentOutsideBG = false;
   *     if (auto bm = as_BB(fBlender)->asBlendMode()) {
   *         SkASSERT(*bm != SkBlendMode::kClear); // Should have been caught at creation time
   *         SkBlendModeCoeff src, dst;
   *         if (SkBlendMode_AsCoeff(*bm, &src, &dst)) {
   *             // If dst's coefficient is 0 then nothing can produce non-transparent content outside
   *             // of the foreground. When dst coefficient is SA, it will always be 0 outside the FG.
   *             // For purposes of transparency analysis, SC == SA.
   *             transparentOutsideFG = dst == SkBlendModeCoeff::kZero || dst == SkBlendModeCoeff::kSA
   *                                                                   || dst == SkBlendModeCoeff::kSC;
   *             // And the reverse is true for src and the background content.
   *             transparentOutsideBG = src == SkBlendModeCoeff::kZero || src == SkBlendModeCoeff::kDA;
   *         }
   *         // NOTE: advanced blends use src-over for their alpha channel, which should produce the
   *         // union of FG and BG. That is the outcome if we leave transparentOutsideFG/BG false.
   *     } else if (fArithmeticCoefficients.has_value()) {
   *         [[maybe_unused]] static constexpr SkV4 kClearCoeff = {0.f, 0.f, 0.f, 0.f};
   *         const SkV4& k = *fArithmeticCoefficients;
   *         SkASSERT(k != kClearCoeff); // Should have been converted to an empty filter
   *
   *         if (k[3] != 0.f) {
   *             // The arithmetic equation produces non-transparent black everywhere
   *             return skif::LayerSpace<SkIRect>::Unbounded();
   *         } else {
   *             // Given the earlier assert and if, then (k[1] == k[2] == 0) implies k[0] != 0. If only
   *             // one of k[1] or k[2] are non-zero then, regardless of k[0], then only that bounds
   *             // has non-transparent content.
   *             transparentOutsideFG = k[2] == 0.f;
   *             transparentOutsideBG = k[1] == 0.f;
   *         }
   *     } else {
   *         // A non-arithmetic runtime blender, so pessimistically assume it can return non-transparent
   *         // black anywhere.
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     }
   *
   *     auto foregroundBounds = this->getChildOutputLayerBounds(kForeground, mapping, contentBounds);
   *     auto backgroundBounds = this->getChildOutputLayerBounds(kBackground, mapping, contentBounds);
   *     if (transparentOutsideFG) {
   *         if (transparentOutsideBG) {
   *             // Output is the intersection of both
   *             if (!foregroundBounds && backgroundBounds) {
   *                 foregroundBounds = *backgroundBounds;
   *             } else if (backgroundBounds && !foregroundBounds->intersect(*backgroundBounds)) {
   *                 return skif::LayerSpace<SkIRect>::Empty();
   *             }
   *             // When both fore and background are infinite, foregroundBounds remains uninstantiated.
   *             // When only foreground is provided, it's left unmodified, which is the correct result.
   *         }
   *         return foregroundBounds;
   *     } else {
   *         if (!transparentOutsideBG) {
   *             // Output is the union of both (infinite blend-induced bounds were detected earlier).
   *             if (foregroundBounds && backgroundBounds) {
   *                 backgroundBounds->join(*foregroundBounds);
   *             } else {
   *                 // At least one of the union arguments is unbounded, so the union is infinite
   *                 backgroundBounds.reset();
   *             }
   *         }
   *         return backgroundBounds;
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
   * sk_sp<SkShader> SkBlendImageFilter::makeBlendShader(sk_sp<SkShader> bg, sk_sp<SkShader> fg) const {
   *     // A null input shader signifies transparent black when image filtering, but SkShaders::Blend
   *     // expects non-null shaders. So we have to do some clean up.
   *     if (!bg || !fg) {
   *         // If we don't affect transparent black and both inputs are null, then return a null
   *         // shader to skip any evaluation.
   *         if (!this->onAffectsTransparentBlack() && !bg && !fg) {
   *             return nullptr;
   *         }
   *         // Otherwise if only one input is null, we might be able to just return that one.
   *         if (auto bm = as_BB(fBlender)->asBlendMode()) {
   *             SkBlendModeCoeff src, dst;
   *             if (SkBlendMode_AsCoeff(*bm, &src, &dst)) {
   *                 if (bg && (dst == SkBlendModeCoeff::kOne ||
   *                            dst == SkBlendModeCoeff::kISA ||
   *                            dst == SkBlendModeCoeff::kISC)) {
   *                     return bg;
   *                 }
   *                 if (fg && (src == SkBlendModeCoeff::kOne ||
   *                            src == SkBlendModeCoeff::kIDA)) {
   *                     return fg;
   *                 }
   *             }
   *         }
   *         // If we made it this far, the blend has non-trivial behavior even when one of the
   *         // inputs is transparent black, so replace the null shaders with that color.
   *         if (!bg) { bg = SkShaders::Color(SK_ColorTRANSPARENT); }
   *         if (!fg) { fg = SkShaders::Color(SK_ColorTRANSPARENT); }
   *     }
   *
   *     return SkShaders::Blend(fBlender, std::move(bg), std::move(fg));
   * }
   * ```
   */
  private fun makeBlendShader(bg: SkSp<SkShader>, fg: SkSp<SkShader>): SkSp<SkShader> {
    TODO("Implement makeBlendShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlendImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 2);
   *
   *     sk_sp<SkBlender> blender;
   *     std::optional<SkV4> coefficients;
   *     bool enforcePremul = false;
   *
   *     const uint32_t mode = buffer.read32();
   *     if (mode == kArithmetic_SkBlendMode) {
   *         // Should only see this sentinel value in newer SKPs
   *         if (buffer.validate(!buffer.isVersionLT(SkPicturePriv::kCombineBlendArithmeticFilters))) {
   *             SkV4 k;
   *             for (int i = 0; i < 4; ++i) {
   *                 k[i] = buffer.readScalar();
   *             }
   *             coefficients = k;
   *             enforcePremul = buffer.readBool();
   *             blender = SkBlenders::Arithmetic(k.x, k.y, k.z, k.w, enforcePremul);
   *             if (!buffer.validate(SkToBool(blender))) {
   *                 return nullptr; // A null arithmetic blender is an error condition
   *             }
   *         }
   *     } else if (mode == kCustom_SkBlendMode) {
   *         blender = buffer.readBlender();
   *     } else {
   *         if (!buffer.validate(mode <= (unsigned) SkBlendMode::kLastMode)) {
   *             return nullptr;
   *         }
   *         blender = SkBlender::Mode((SkBlendMode)mode);
   *     }
   *
   *     return make_blend(std::move(blender),
   *                       common.getInput(kBackground),
   *                       common.getInput(kForeground),
   *                       common.cropRect(),
   *                       coefficients,
   *                       enforcePremul);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    private val kBackground: Int = TODO("Initialize kBackground")

    private val kForeground: Int = TODO("Initialize kForeground")

    private val kArithmeticSkBlendMode: UInt = TODO("Initialize kArithmeticSkBlendMode")

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFlattenable> SkBlendImageFilter::LegacyArithmeticCreateProc(SkReadBuffer& buffer) {
     *     // Newer SKPs should be using the updated Blend CreateProc.
     *     if (!buffer.validate(buffer.isVersionLT(SkPicturePriv::kCombineBlendArithmeticFilters))) {
     *         SkASSERT(false); // debug-only, so release will just see a failed deserialization
     *         return nullptr;
     *     }
     *
     *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 2);
     *     float k[4];
     *     for (int i = 0; i < 4; ++i) {
     *         k[i] = buffer.readScalar();
     *     }
     *     const bool enforcePremul = buffer.readBool();
     *     return SkImageFilters::Arithmetic(k[0], k[1], k[2], k[3], enforcePremul,
     *                                       common.getInput(0), common.getInput(1), common.cropRect());
     * }
     * ```
     */
    private fun legacyArithmeticCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement legacyArithmeticCreateProc")
    }
  }
}
