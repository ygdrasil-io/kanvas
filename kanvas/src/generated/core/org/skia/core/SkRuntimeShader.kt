package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.sksl.DebugTracePriv
import org.skia.tests.ShaderType
import undefined.UniformsCallback

/**
 * C++ original:
 * ```cpp
 * class SkRuntimeShader : public SkShaderBase {
 * public:
 *     SkRuntimeShader(sk_sp<SkRuntimeEffect> effect,
 *                     sk_sp<SkSL::DebugTracePriv> debugTrace,
 *                     sk_sp<const SkData> uniforms,
 *                     SkSpan<const SkRuntimeEffect::ChildPtr> children);
 *
 *     SkRuntimeShader(sk_sp<SkRuntimeEffect> effect,
 *                     sk_sp<SkSL::DebugTracePriv> debugTrace,
 *                     UniformsCallback uniformsCallback,
 *                     SkSpan<const SkRuntimeEffect::ChildPtr> children);
 *
 *     SkRuntimeEffect::TracedShader makeTracedClone(const SkIPoint& coord);
 *
 *     bool isOpaque() const override { return fEffect->alwaysOpaque(); }
 *
 *     ShaderType type() const override { return ShaderType::kRuntime; }
 *
 *     bool appendStages(const SkStageRec& rec, const SkShaders::MatrixRec& mRec) const override;
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     SkRuntimeEffect* asRuntimeEffect() const override { return fEffect.get(); }
 *
 *     sk_sp<SkRuntimeEffect> effect() const { return fEffect; }
 *     SkSpan<const SkRuntimeEffect::ChildPtr> children() const { return fChildren; }
 *
 *     sk_sp<const SkData> uniformData(const SkColorSpace* dstCS) const;
 *
 *     SK_FLATTENABLE_HOOKS(SkRuntimeShader)
 *
 * private:
 *     enum Flags {
 *         kHasLegacyLocalMatrix_Flag = 1 << 1,
 *     };
 *
 *     sk_sp<SkRuntimeEffect> fEffect;
 *     sk_sp<SkSL::DebugTracePriv> fDebugTrace;
 *     sk_sp<const SkData> fUniformData;
 *     UniformsCallback fUniformsCallback;
 *     std::vector<SkRuntimeEffect::ChildPtr> fChildren;
 * }
 * ```
 */
public open class SkRuntimeShader public constructor(
  effect: SkSp<SkRuntimeEffect>,
  debugTrace: SkSp<DebugTracePriv>,
  uniforms: SkSp<SkData>,
  children: SkSpan<SkRuntimeEffect.ChildPtr>,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fEffect
   * ```
   */
  private var fEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSL::DebugTracePriv> fDebugTrace
   * ```
   */
  private var fDebugTrace: SkSp<DebugTracePriv> = TODO("Initialize fDebugTrace")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> fUniformData
   * ```
   */
  private val fUniformData: SkSp<SkData> = TODO("Initialize fUniformData")

  /**
   * C++ original:
   * ```cpp
   * UniformsCallback fUniformsCallback
   * ```
   */
  private var fUniformsCallback: Int = TODO("Initialize fUniformsCallback")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkRuntimeEffect::ChildPtr> fChildren
   * ```
   */
  private var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeShader::SkRuntimeShader(sk_sp<SkRuntimeEffect> effect,
   *                                  sk_sp<SkSL::DebugTracePriv> debugTrace,
   *                                  sk_sp<const SkData> uniforms,
   *                                  SkSpan<const SkRuntimeEffect::ChildPtr> children)
   *         : fEffect(std::move(effect))
   *         , fDebugTrace(std::move(debugTrace))
   *         , fUniformData(std::move(uniforms))
   *         , fChildren(children.begin(), children.end()) {}
   * ```
   */
  public constructor(
    effect: SkSp<SkRuntimeEffect>,
    debugTrace: SkSp<DebugTracePriv>,
    uniformsCallback: UniformsCallback,
    children: SkSpan<SkRuntimeEffect.ChildPtr>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeEffect::TracedShader SkRuntimeShader::makeTracedClone(const SkIPoint& coord) {
   *     sk_sp<SkRuntimeEffect> unoptimized = fEffect->makeUnoptimizedClone();
   *     sk_sp<SkSL::DebugTracePriv> debugTrace = make_debug_trace(unoptimized.get(), coord);
   *     auto debugShader = sk_make_sp<SkRuntimeShader>(
   *             unoptimized, debugTrace, this->uniformData(nullptr), SkSpan(fChildren));
   *
   *     return SkRuntimeEffect::TracedShader{std::move(debugShader), std::move(debugTrace)};
   * }
   * ```
   */
  public fun makeTracedClone(coord: SkIPoint): SkRuntimeEffect.TracedShader {
    TODO("Implement makeTracedClone")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const override { return fEffect->alwaysOpaque(); }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kRuntime; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRuntimeShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec& mRec) const {
   *     if (!SkRuntimeEffectPriv::CanDraw(SkCapabilities::RasterBackend().get(), fEffect.get())) {
   *         // SkRP has support for many parts of #version 300 already, but for now, we restrict its
   *         // usage in runtime effects to just #version 100.
   *         return false;
   *     }
   *     if (const SkSL::RP::Program* program = fEffect->getRPProgram(fDebugTrace.get())) {
   *         std::optional<SkShaders::MatrixRec> newMRec = mRec.apply(rec);
   *         if (!newMRec.has_value()) {
   *             return false;
   *         }
   *         SkSpan<const float> uniforms =
   *                 SkRuntimeEffectPriv::UniformsAsSpan(fEffect->uniforms(),
   *                                                     this->uniformData(rec.fDstCS),
   *                                                     /*alwaysCopyIntoAlloc=*/fUniformData == nullptr,
   *                                                     rec.fDstCS,
   *                                                     rec.fAlloc);
   *         RuntimeEffectRPCallbacks callbacks(rec, *newMRec, fChildren, fEffect->fSampleUsages);
   *         bool success = program->appendStages(rec.fPipeline, rec.fAlloc, &callbacks, uniforms);
   *         return success;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRuntimeShader::flatten(SkWriteBuffer& buffer) const {
   *     if (SkKnownRuntimeEffects::IsSkiaKnownRuntimeEffect(fEffect->fStableKey)) {
   *         // We only serialize Skia-internal stableKeys. First party stable keys are not serialized.
   *         buffer.write32(fEffect->fStableKey);
   *     } else {
   *         buffer.write32(0);
   *         buffer.writeString(fEffect->source().c_str());
   *     }
   *     buffer.writeDataAsByteArray(this->uniformData(nullptr).get());
   *     SkRuntimeEffectPriv::WriteChildEffects(buffer, fChildren);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeEffect* asRuntimeEffect() const override { return fEffect.get(); }
   * ```
   */
  public override fun asRuntimeEffect(): SkRuntimeEffect {
    TODO("Implement asRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> effect() const { return fEffect; }
   * ```
   */
  public fun effect(): SkSp<SkRuntimeEffect> {
    TODO("Implement effect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkRuntimeEffect::ChildPtr> children() const { return fChildren; }
   * ```
   */
  public fun children(): SkSpan<SkRuntimeEffect.ChildPtr> {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> SkRuntimeShader::uniformData(const SkColorSpace* dstCS) const {
   *     if (fUniformData) {
   *         return fUniformData;
   *     }
   *
   *     // We want to invoke the uniforms-callback each time a paint occurs.
   *     SkASSERT(fUniformsCallback);
   *     sk_sp<const SkData> uniforms = fUniformsCallback({dstCS});
   *     SkASSERT(uniforms && uniforms->size() == fEffect->uniformSize());
   *     return uniforms;
   * }
   * ```
   */
  public fun uniformData(dstCS: SkColorSpace?): SkSp<SkData> {
    TODO("Implement uniformData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkRuntimeShader::CreateProc(SkReadBuffer& buffer) {
   *     if (!buffer.validate(buffer.allowSkSL())) {
   *         return nullptr;
   *     }
   *
   *     sk_sp<SkRuntimeEffect> effect;
   *     if (!buffer.isVersionLT(SkPicturePriv::kSerializeStableKeys)) {
   *         uint32_t candidateStableKey = buffer.readUInt();
   *         effect = SkKnownRuntimeEffects::MaybeGetKnownRuntimeEffect(candidateStableKey);
   *         if (!effect && !buffer.validate(candidateStableKey == 0)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     if (!effect) {
   *         SkString sksl;
   *         buffer.readString(&sksl);
   *         effect = SkMakeCachedRuntimeEffect(SkRuntimeEffect::MakeForShader, std::move(sksl));
   *     }
   *     if constexpr (!kLenientSkSLDeserialization) {
   *         if (!buffer.validate(effect != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     sk_sp<SkData> uniforms = buffer.readByteArrayAsData();
   *
   *     std::optional<SkMatrix> localM;
   *     if (buffer.isVersionLT(SkPicturePriv::kNoShaderLocalMatrix)) {
   *         uint32_t flags = buffer.read32();
   *         if (flags & kHasLegacyLocalMatrix_Flag) {
   *             buffer.readMatrix(&localM.emplace());
   *         }
   *     }
   *
   *     skia_private::STArray<4, SkRuntimeEffect::ChildPtr> children;
   *     if (!SkRuntimeEffectPriv::ReadChildEffects(buffer, effect.get(), &children)) {
   *         return nullptr;
   *     }
   *
   *     if constexpr (kLenientSkSLDeserialization) {
   *         if (!effect) {
   *             // If any children were SkShaders, return the first one. This is a reasonable fallback.
   *             for (int i = 0; i < children.size(); i++) {
   *                 if (children[i].shader()) {
   *                     SkDebugf("Serialized SkSL failed to compile. Replacing shader with child %d.\n",
   *                              i);
   *                     return sk_ref_sp(children[i].shader());
   *                 }
   *             }
   *
   *             // We don't know what to do, so just return nullptr (but *don't* poison the buffer).
   *             SkDebugf("Serialized SkSL failed to compile. Ignoring/dropping SkSL shader.\n");
   *             return nullptr;
   *         }
   *     }
   *
   *     return effect->makeShader(std::move(uniforms), SkSpan(children), SkOptAddressOrNull(localM));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public enum class Flags {
    kHasLegacyLocalMatrix_Flag,
  }
}
