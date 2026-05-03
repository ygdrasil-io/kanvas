package org.skia.core

import UniformsCallback
import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkData
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.memory.SkArenaAlloc
import org.skia.sksl.Context
import org.skia.sksl.Program
import org.skia.sksl.Variable

/**
 * C++ original:
 * ```cpp
 * class SkRuntimeEffectPriv {
 * public:
 *     struct UniformsCallbackContext {
 *         const SkColorSpace* fDstColorSpace;
 *     };
 *
 *     // Private (experimental) API for creating runtime shaders with late-bound uniforms.
 *     // The callback must produce a uniform data blob of the correct size for the effect.
 *     // It is invoked at "draw" time (essentially, when a draw call is made against the canvas
 *     // using the resulting shader). There are no strong guarantees about timing.
 *     // Serializing the resulting shader will immediately invoke the callback (and record the
 *     // resulting uniforms).
 *     using UniformsCallback = std::function<sk_sp<const SkData>(const UniformsCallbackContext&)>;
 *     static sk_sp<SkShader> MakeDeferredShader(const SkRuntimeEffect* effect,
 *                                               UniformsCallback uniformsCallback,
 *                                               SkSpan<const SkRuntimeEffect::ChildPtr> children,
 *                                               const SkMatrix* localMatrix = nullptr);
 *
 *     // Helper function when creating an effect for a GrSkSLFP that verifies an effect will
 *     // implement the GrFragmentProcessor "constant output for constant input" optimization flag.
 *     static bool SupportsConstantOutputForConstantInput(const SkRuntimeEffect* effect) {
 *         // This optimization is only implemented for color filters without any children.
 *         if (!effect->allowColorFilter() || !effect->children().empty()) {
 *             return false;
 *         }
 *         return true;
 *     }
 *
 *     static uint32_t Hash(const SkRuntimeEffect& effect) {
 *         return effect.hash();
 *     }
 *
 *     static bool HasName(const SkRuntimeEffect& effect) {
 *         return !effect.fName.isEmpty();
 *     }
 *
 *     static const char* GetName(const SkRuntimeEffect& effect) {
 *         return effect.fName.c_str();
 *     }
 *
 *     static uint32_t StableKey(const SkRuntimeEffect& effect) {
 *         return effect.fStableKey;
 *     }
 *
 *     // This method is only used on user-defined known runtime effects
 *     static void SetStableKey(SkRuntimeEffect* effect, uint32_t stableKey) {
 *         SkASSERT(!effect->fStableKey);
 *         SkASSERT(SkKnownRuntimeEffects::IsViableUserDefinedKnownRuntimeEffect(stableKey));
 *         effect->fStableKey = stableKey;
 *     }
 *
 *     // This method is only used for Skia-internal known runtime effects
 *     static void SetStableKeyOnOptions(SkRuntimeEffect::Options* options, uint32_t stableKey) {
 *         SkASSERT(!options->fStableKey);
 *         SkASSERT(SkKnownRuntimeEffects::IsSkiaKnownRuntimeEffect(stableKey));
 *         options->fStableKey = stableKey;
 *     }
 *
 *     static void ResetStableKey(SkRuntimeEffect* effect) {
 *         effect->fStableKey = 0;
 *     }
 *
 *     static const SkSL::Program& Program(const SkRuntimeEffect& effect) {
 *         return *effect.fBaseProgram;
 *     }
 *
 *     static SkRuntimeEffect::Options ES3Options() {
 *         SkRuntimeEffect::Options options;
 *         options.maxVersionAllowed = SkSL::Version::k300;
 *         return options;
 *     }
 *
 *     static void AllowPrivateAccess(SkRuntimeEffect::Options* options) {
 *         options->allowPrivateAccess = true;
 *     }
 *
 *     static SkRuntimeEffect::Uniform VarAsUniform(const SkSL::Variable&,
 *                                                  const SkSL::Context&,
 *                                                  size_t* offset);
 *
 *     static SkRuntimeEffect::Child VarAsChild(const SkSL::Variable& var,
 *                                              int index);
 *
 *     static const char* ChildTypeToStr(SkRuntimeEffect::ChildType type);
 *
 *     // If there are layout(color) uniforms then this performs color space transformation on the
 *     // color values and returns a new SkData. Otherwise, the original data is returned.
 *     static sk_sp<const SkData> TransformUniforms(SkSpan<const SkRuntimeEffect::Uniform> uniforms,
 *                                                  sk_sp<const SkData> originalData,
 *                                                  const SkColorSpaceXformSteps&);
 *     static sk_sp<const SkData> TransformUniforms(SkSpan<const SkRuntimeEffect::Uniform> uniforms,
 *                                                  sk_sp<const SkData> originalData,
 *                                                  const SkColorSpace* dstCS);
 *     static SkSpan<const float> UniformsAsSpan(
 *         SkSpan<const SkRuntimeEffect::Uniform> uniforms,
 *         sk_sp<const SkData> originalData,
 *         bool alwaysCopyIntoAlloc,
 *         const SkColorSpace* destColorSpace,
 *         SkArenaAlloc* alloc);
 *
 *     static bool CanDraw(const SkCapabilities*, const SkSL::Program*);
 *     static bool CanDraw(const SkCapabilities*, const SkRuntimeEffect*);
 *
 *     static bool ReadChildEffects(SkReadBuffer& buffer,
 *                                  const SkRuntimeEffect* effect,
 *                                  skia_private::TArray<SkRuntimeEffect::ChildPtr>* children);
 *     static void WriteChildEffects(SkWriteBuffer& buffer,
 *                                   SkSpan<const SkRuntimeEffect::ChildPtr> children);
 *
 *     static bool UsesColorTransform(const SkRuntimeEffect* effect) {
 *         return effect->usesColorTransform();
 *     }
 *     static SkSL::SampleUsage ChildSampleUsage(const SkRuntimeEffect* effect, int child) {
 *         return effect->fSampleUsages[child];
 *     }
 * }
 * ```
 */
public open class SkRuntimeEffectPriv {
  public data class UniformsCallbackContext public constructor(
    public val fDstColorSpace: SkColorSpace?,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkRuntimeEffectPriv::MakeDeferredShader(
     *         const SkRuntimeEffect* effect,
     *         UniformsCallback uniformsCallback,
     *         SkSpan<const SkRuntimeEffect::ChildPtr> children,
     *         const SkMatrix* localMatrix) {
     *     if (!effect->allowShader()) {
     *         return nullptr;
     *     }
     *     if (!verify_child_effects(effect->fChildren, children)) {
     *         return nullptr;
     *     }
     *     if (!uniformsCallback) {
     *         return nullptr;
     *     }
     *     return SkLocalMatrixShader::MakeWrapped<SkRuntimeShader>(localMatrix,
     *                                                              sk_ref_sp(effect),
     *                                                              /*debugTrace=*/nullptr,
     *                                                              std::move(uniformsCallback),
     *                                                              children);
     * }
     * ```
     */
    public fun makeDeferredShader(
      effect: SkRuntimeEffect?,
      uniformsCallback: UniformsCallback,
      children: SkSpan<SkRuntimeEffect.ChildPtr>,
      localMatrix: SkMatrix? = null,
    ): SkSp<SkShader> {
      TODO("Implement makeDeferredShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool SupportsConstantOutputForConstantInput(const SkRuntimeEffect* effect) {
     *         // This optimization is only implemented for color filters without any children.
     *         if (!effect->allowColorFilter() || !effect->children().empty()) {
     *             return false;
     *         }
     *         return true;
     *     }
     * ```
     */
    public fun supportsConstantOutputForConstantInput(effect: SkRuntimeEffect?): Boolean {
      TODO("Implement supportsConstantOutputForConstantInput")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t Hash(const SkRuntimeEffect& effect) {
     *         return effect.hash();
     *     }
     * ```
     */
    public fun hash(effect: SkRuntimeEffect): UInt {
      TODO("Implement hash")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool HasName(const SkRuntimeEffect& effect) {
     *         return !effect.fName.isEmpty();
     *     }
     * ```
     */
    public fun hasName(effect: SkRuntimeEffect): Boolean {
      TODO("Implement hasName")
    }

    /**
     * C++ original:
     * ```cpp
     * static const char* GetName(const SkRuntimeEffect& effect) {
     *         return effect.fName.c_str();
     *     }
     * ```
     */
    public fun getName(effect: SkRuntimeEffect): Char {
      TODO("Implement getName")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t StableKey(const SkRuntimeEffect& effect) {
     *         return effect.fStableKey;
     *     }
     * ```
     */
    public fun stableKey(effect: SkRuntimeEffect): UInt {
      TODO("Implement stableKey")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetStableKey(SkRuntimeEffect* effect, uint32_t stableKey) {
     *         SkASSERT(!effect->fStableKey);
     *         SkASSERT(SkKnownRuntimeEffects::IsViableUserDefinedKnownRuntimeEffect(stableKey));
     *         effect->fStableKey = stableKey;
     *     }
     * ```
     */
    public fun setStableKey(effect: SkRuntimeEffect?, stableKey: UInt) {
      TODO("Implement setStableKey")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetStableKeyOnOptions(SkRuntimeEffect::Options* options, uint32_t stableKey) {
     *         SkASSERT(!options->fStableKey);
     *         SkASSERT(SkKnownRuntimeEffects::IsSkiaKnownRuntimeEffect(stableKey));
     *         options->fStableKey = stableKey;
     *     }
     * ```
     */
    public fun setStableKeyOnOptions(options: SkRuntimeEffect.Options?, stableKey: UInt) {
      TODO("Implement setStableKeyOnOptions")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ResetStableKey(SkRuntimeEffect* effect) {
     *         effect->fStableKey = 0;
     *     }
     * ```
     */
    public fun resetStableKey(effect: SkRuntimeEffect?) {
      TODO("Implement resetStableKey")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkSL::Program& Program(const SkRuntimeEffect& effect) {
     *         return *effect.fBaseProgram;
     *     }
     * ```
     */
    public fun program(effect: SkRuntimeEffect): Program {
      TODO("Implement program")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRuntimeEffect::Options ES3Options() {
     *         SkRuntimeEffect::Options options;
     *         options.maxVersionAllowed = SkSL::Version::k300;
     *         return options;
     *     }
     * ```
     */
    public fun eS3Options(): SkRuntimeEffect.Options {
      TODO("Implement eS3Options")
    }

    /**
     * C++ original:
     * ```cpp
     * static void AllowPrivateAccess(SkRuntimeEffect::Options* options) {
     *         options->allowPrivateAccess = true;
     *     }
     * ```
     */
    public fun allowPrivateAccess(options: SkRuntimeEffect.Options?) {
      TODO("Implement allowPrivateAccess")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Uniform SkRuntimeEffectPriv::VarAsUniform(const SkSL::Variable& var,
     *                                                            const SkSL::Context& context,
     *                                                            size_t* offset) {
     *     using Uniform = SkRuntimeEffect::Uniform;
     *     SkASSERT(var.modifierFlags().isUniform());
     *     Uniform uni;
     *     uni.name = var.name();
     *     uni.flags = 0;
     *     uni.count = 1;
     *
     *     const SkSL::Type* type = &var.type();
     *     if (type->isArray()) {
     *         uni.flags |= Uniform::kArray_Flag;
     *         uni.count = type->columns();
     *         type = &type->componentType();
     *     }
     *
     *     if (type->hasPrecision() && !type->highPrecision()) {
     *         uni.flags |= Uniform::kHalfPrecision_Flag;
     *     }
     *
     *     SkAssertResult(init_uniform_type(context, type, &uni));
     *     if (var.layout().fFlags & SkSL::LayoutFlag::kColor) {
     *         uni.flags |= Uniform::kColor_Flag;
     *     }
     *
     *     uni.offset = *offset;
     *     *offset += uni.sizeInBytes();
     *     SkASSERT(SkIsAlign4(*offset));
     *     return uni;
     * }
     * ```
     */
    public fun varAsUniform(
      `var`: Variable,
      context: Context,
      offset: ULong?,
    ): SkRuntimeEffect.Uniform {
      TODO("Implement varAsUniform")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRuntimeEffect::Child SkRuntimeEffectPriv::VarAsChild(const SkSL::Variable& var, int index) {
     *     SkRuntimeEffect::Child c;
     *     c.name  = var.name();
     *     c.type  = child_type(var.type());
     *     c.index = index;
     *     return c;
     * }
     * ```
     */
    public fun varAsChild(`var`: Variable, index: Int): SkRuntimeEffect.Child {
      TODO("Implement varAsChild")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkRuntimeEffectPriv::ChildTypeToStr(ChildType type) {
     *     switch (type) {
     *         case ChildType::kBlender:     return "blender";
     *         case ChildType::kColorFilter: return "color filter";
     *         case ChildType::kShader:      return "shader";
     *         default: SkUNREACHABLE;
     *     }
     * }
     * ```
     */
    public fun childTypeToStr(type: SkRuntimeEffect.ChildType): Char {
      TODO("Implement childTypeToStr")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<const SkData> SkRuntimeEffectPriv::TransformUniforms(
     *         SkSpan<const SkRuntimeEffect::Uniform> uniforms,
     *         sk_sp<const SkData> originalData,
     *         const SkColorSpaceXformSteps& steps) {
     *     using Flags = SkRuntimeEffect::Uniform::Flags;
     *     using Type  = SkRuntimeEffect::Uniform::Type;
     *
     *     sk_sp<SkData> data = nullptr;
     *     auto writableData = [&]() {
     *         if (!data) {
     *             data = SkData::MakeWithCopy(originalData->data(), originalData->size());
     *         }
     *         return data->writable_data();
     *     };
     *
     *     for (const auto& u : uniforms) {
     *         if (u.flags & Flags::kColor_Flag) {
     *             SkASSERT(u.type == Type::kFloat3 || u.type == Type::kFloat4);
     *             if (steps.fFlags.mask()) {
     *                 float* color = SkTAddOffset<float>(writableData(), u.offset);
     *                 if (u.type == Type::kFloat4) {
     *                     // RGBA, easy case
     *                     for (int i = 0; i < u.count; ++i) {
     *                         steps.apply(color);
     *                         color += 4;
     *                     }
     *                 } else {
     *                     // RGB, need to pad out to include alpha. Technically, this isn't necessary,
     *                     // because steps shouldn't include unpremul or premul, and thus shouldn't
     *                     // read or write the fourth element. But let's be safe.
     *                     float rgba[4];
     *                     for (int i = 0; i < u.count; ++i) {
     *                         memcpy(rgba, color, 3 * sizeof(float));
     *                         rgba[3] = 1.0f;
     *                         steps.apply(rgba);
     *                         memcpy(color, rgba, 3 * sizeof(float));
     *                         color += 3;
     *                     }
     *                 }
     *             }
     *         }
     *     }
     *     return data ? data : originalData;
     * }
     * ```
     */
    public fun transformUniforms(
      uniforms: SkSpan<SkRuntimeEffect.Uniform>,
      originalData: SkSp<SkData>,
      steps: SkColorSpaceXformSteps,
    ): SkSp<SkData> {
      TODO("Implement transformUniforms")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<const SkData> SkRuntimeEffectPriv::TransformUniforms(
     *         SkSpan<const SkRuntimeEffect::Uniform> uniforms,
     *         sk_sp<const SkData> originalData,
     *         const SkColorSpace* dstCS) {
     *     if (!dstCS) {
     *         // There's no destination color-space; we can early-out immediately.
     *         return originalData;
     *     }
     *     SkColorSpaceXformSteps steps(sk_srgb_singleton(), kUnpremul_SkAlphaType,
     *                                  dstCS,               kUnpremul_SkAlphaType);
     *     return TransformUniforms(uniforms, std::move(originalData), steps);
     * }
     * ```
     */
    public fun transformUniforms(
      uniforms: SkSpan<SkRuntimeEffect.Uniform>,
      originalData: SkSp<SkData>,
      dstCS: SkColorSpace?,
    ): SkSp<SkData> {
      TODO("Implement transformUniforms")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSpan<const float> SkRuntimeEffectPriv::UniformsAsSpan(
     *         SkSpan<const SkRuntimeEffect::Uniform> uniforms,
     *         sk_sp<const SkData> originalData,
     *         bool alwaysCopyIntoAlloc,
     *         const SkColorSpace* destColorSpace,
     *         SkArenaAlloc* alloc) {
     *     // Transform the uniforms into the destination colorspace.
     *     sk_sp<const SkData> transformedData = SkRuntimeEffectPriv::TransformUniforms(uniforms,
     *                                                                                  originalData,
     *                                                                                  destColorSpace);
     *     if (alwaysCopyIntoAlloc || originalData != transformedData) {
     *         // The transformed uniform data's lifetime is not long enough to reuse; instead, we copy the
     *         // uniform data directly into the alloc.
     *         size_t numBytes = transformedData->size();
     *         size_t numFloats = numBytes / sizeof(float);
     *         float* uniformsInAlloc = alloc->makeArrayDefault<float>(numFloats);
     *         memcpy(uniformsInAlloc, transformedData->data(), numBytes);
     *         return {uniformsInAlloc, numFloats};
     *     }
     *     // It's safe to return a pointer into existing data.
     *     return SkSpan{static_cast<const float*>(originalData->data()),
     *                   originalData->size() / sizeof(float)};
     * }
     * ```
     */
    public fun uniformsAsSpan(
      uniforms: SkSpan<SkRuntimeEffect.Uniform>,
      originalData: SkSp<SkData>,
      alwaysCopyIntoAlloc: Boolean,
      destColorSpace: SkColorSpace?,
      alloc: SkArenaAlloc?,
    ): SkSpan<Float> {
      TODO("Implement uniformsAsSpan")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRuntimeEffectPriv::CanDraw(const SkCapabilities* caps, const SkSL::Program* program) {
     *     SkASSERT(caps && program);
     *     SkASSERT(program->fConfig->enforcesSkSLVersion());
     *     return program->fConfig->fRequiredSkSLVersion <= caps->skslVersion();
     * }
     * ```
     */
    public fun canDraw(caps: SkCapabilities?, program: Program?): Boolean {
      TODO("Implement canDraw")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRuntimeEffectPriv::CanDraw(const SkCapabilities* caps, const SkRuntimeEffect* effect) {
     *     SkASSERT(effect);
     *     return CanDraw(caps, effect->fBaseProgram.get());
     * }
     * ```
     */
    public fun canDraw(caps: SkCapabilities?, effect: SkRuntimeEffect?): Boolean {
      TODO("Implement canDraw")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRuntimeEffectPriv::ReadChildEffects(SkReadBuffer& buffer,
     *                                            const SkRuntimeEffect* effect,
     *                                            TArray<SkRuntimeEffect::ChildPtr>* children) {
     *     size_t childCount = buffer.read32();
     *     if (effect && !buffer.validate(childCount == effect->children().size())) {
     *         return false;
     *     }
     *
     *     children->clear();
     *     children->reserve_exact(childCount);
     *
     *     for (size_t i = 0; i < childCount; i++) {
     *         sk_sp<SkFlattenable> obj(buffer.readRawFlattenable());
     *         if (!flattenable_is_valid_as_child(obj.get())) {
     *             buffer.validate(false);
     *             return false;
     *         }
     *         children->push_back(std::move(obj));
     *     }
     *
     *     // If we are validating against an effect, make sure any (non-null) children are the right type
     *     if (effect) {
     *         auto childInfo = effect->children();
     *         SkASSERT(childInfo.size() == SkToSizeT(children->size()));
     *         for (size_t i = 0; i < childCount; i++) {
     *             std::optional<ChildType> ct = (*children)[i].type();
     *             if (ct.has_value() && (*ct) != childInfo[i].type) {
     *                 buffer.validate(false);
     *             }
     *         }
     *     }
     *
     *     return buffer.isValid();
     * }
     * ```
     */
    public fun readChildEffects(
      buffer: SkReadBuffer,
      effect: SkRuntimeEffect?,
      children: TArray<SkRuntimeEffect.ChildPtr>?,
    ): Boolean {
      TODO("Implement readChildEffects")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkRuntimeEffectPriv::WriteChildEffects(
     *         SkWriteBuffer& buffer, SkSpan<const SkRuntimeEffect::ChildPtr> children) {
     *     buffer.write32(children.size());
     *     for (const auto& child : children) {
     *         buffer.writeFlattenable(child.flattenable());
     *     }
     * }
     * ```
     */
    public fun writeChildEffects(buffer: SkWriteBuffer, children: SkSpan<SkRuntimeEffect.ChildPtr>) {
      TODO("Implement writeChildEffects")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool UsesColorTransform(const SkRuntimeEffect* effect) {
     *         return effect->usesColorTransform();
     *     }
     * ```
     */
    public fun usesColorTransform(effect: SkRuntimeEffect?): Boolean {
      TODO("Implement usesColorTransform")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSL::SampleUsage ChildSampleUsage(const SkRuntimeEffect* effect, int child) {
     *         return effect->fSampleUsages[child];
     *     }
     * ```
     */
    public fun childSampleUsage(effect: SkRuntimeEffect?, child: Int): SampleUsage {
      TODO("Implement childSampleUsage")
    }
  }
}
