package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.effects.SkRuntimeEffect

/**
 * C++ original:
 * ```cpp
 * struct RuntimeEffectBlock {
 *     struct ShaderData {
 *         // This ctor is used during pre-compilation when we don't have enough information to
 *         // extract uniform data.
 *         ShaderData(sk_sp<const SkRuntimeEffect> effect);
 *
 *         // This ctor is used when extracting information from PaintParams.
 *         ShaderData(sk_sp<const SkRuntimeEffect> effect,
 *                    sk_sp<const SkData> uniforms);
 *
 *         bool operator==(const ShaderData& rhs) const;
 *         bool operator!=(const ShaderData& rhs) const { return !(*this == rhs); }
 *
 *         // Runtime shader data.
 *         sk_sp<const SkRuntimeEffect> fEffect;
 *         sk_sp<const SkData>          fUniforms;
 *     };
 *
 *     // On a false return, no block has been started
 *     static bool BeginBlock(const KeyContext&, const ShaderData&);
 *
 *     // Add a no-op placeholder for an incorrect runtime effect
 *     static void AddNoOpEffect(const KeyContext&, SkRuntimeEffect*);
 *
 *     // Add a post-amble for runtime effects that use the toLinearSrgb/fromLinearSrgb intrinsics
 *     static void HandleIntrinsics(const KeyContext&, const SkRuntimeEffect*);
 * }
 * ```
 */
public open class RuntimeEffectBlock {
  public data class ShaderData public constructor(
    public var skSp: undefined.ShaderData,
    public var fEffect: Int,
    public var fUniforms: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool RuntimeEffectBlock::BeginBlock(const KeyContext& keyContext, const ShaderData& shaderData) {
     *     ShaderCodeDictionary* dict = keyContext.dict();
     *     int codeSnippetID = dict->findOrCreateRuntimeEffectSnippet(shaderData.fEffect.get());
     *
     *     if (codeSnippetID < 0) {
     *         return false;
     *     }
     *
     *     if (SkKnownRuntimeEffects::IsUserDefinedRuntimeEffect(codeSnippetID)) {
     *         keyContext.rtEffectDict()->set(codeSnippetID, shaderData.fEffect);
     *     }
     *
     *     const ShaderSnippet* entry = dict->getEntry(codeSnippetID);
     *     if (!entry) {
     *         return false;
     *     }
     *
     *     gather_runtime_effect_uniforms(keyContext,
     *                                    shaderData.fEffect.get(),
     *                                    entry->fUniforms,
     *                                    shaderData.fUniforms.get(),
     *                                    keyContext.pipelineDataGatherer());
     *
     *     keyContext.paintParamsKeyBuilder()->beginBlock(codeSnippetID);
     *     return true;
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext, shaderData: ShaderData): Boolean {
      TODO("Implement beginBlock")
    }

    /**
     * C++ original:
     * ```cpp
     * void RuntimeEffectBlock::AddNoOpEffect(const KeyContext& keyContext, SkRuntimeEffect* effect) {
     *     if (effect->allowShader()) {
     *         // A missing shader returns transparent black
     *         SolidColorShaderBlock::AddBlock(keyContext, SK_PMColor4fTRANSPARENT);
     *     } else if (effect->allowColorFilter()) {
     *         // A "passthrough" color filter returns the input color as-is.
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
     *     } else {
     *         SkASSERT(effect->allowBlender());
     *         // A "passthrough" blender performs `blend_src_over(src, dest)`.
     *         AddFixedBlendMode(keyContext, SkBlendMode::kSrcOver);
     *     }
     * }
     * ```
     */
    public fun addNoOpEffect(keyContext: KeyContext, effect: SkRuntimeEffect?) {
      TODO("Implement addNoOpEffect")
    }

    /**
     * C++ original:
     * ```cpp
     * void RuntimeEffectBlock::HandleIntrinsics(const KeyContext& keyContext, const SkRuntimeEffect* effect) {
     *     // Runtime effects that reference color transform intrinsics have two extra children that
     *     // are bound to the colorspace xform snippet with values to go to and from the linear srgb
     *     // to the current working/dst color space.
     *     if (SkRuntimeEffectPriv::UsesColorTransform(effect)) {
     *         SkColorSpace* dstCS = keyContext.dstColorInfo().colorSpace();
     *         if (!dstCS) {
     *             dstCS = sk_srgb_linear_singleton(); // turn colorspace conversion into a noop
     *         }
     *
     *         // TODO(b/332565302): If the runtime shader only uses one of these transforms, we could
     *         // upload only one set of uniforms.
     *
     *         // NOTE: This must be kept in sync with the logic used to generate the toLinearSrgb() and
     *         // fromLinearSrgb() expressions for each runtime effect. toLinearSrgb() is assumed to be
     *         // the second to last child, and fromLinearSrgb() is assumed to be the last.
     *         ColorSpaceTransformBlock::ColorSpaceTransformData dstToLinear(dstCS,
     *                                                                       kUnpremul_SkAlphaType,
     *                                                                       sk_srgb_linear_singleton(),
     *                                                                       kUnpremul_SkAlphaType);
     *         ColorSpaceTransformBlock::ColorSpaceTransformData linearToDst(sk_srgb_linear_singleton(),
     *                                                                       kUnpremul_SkAlphaType,
     *                                                                       dstCS,
     *                                                                       kUnpremul_SkAlphaType);
     *
     *         ColorSpaceTransformBlock::AddBlock(keyContext, dstToLinear);
     *         ColorSpaceTransformBlock::AddBlock(keyContext, linearToDst);
     *     }
     * }
     * ```
     */
    public fun handleIntrinsics(keyContext: KeyContext, effect: SkRuntimeEffect?) {
      TODO("Implement handleIntrinsics")
    }
  }
}
