package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.tests.BlenderType

/**
 * C++ original:
 * ```cpp
 * class SkRuntimeBlender : public SkBlenderBase {
 * public:
 *     SkRuntimeBlender(sk_sp<SkRuntimeEffect> effect,
 *                      sk_sp<const SkData> uniforms,
 *                      SkSpan<const SkRuntimeEffect::ChildPtr> children)
 *             : fEffect(std::move(effect))
 *             , fUniforms(std::move(uniforms))
 *             , fChildren(children.begin(), children.end()) {}
 *
 *     SkRuntimeEffect* asRuntimeEffect() const override { return fEffect.get(); }
 *
 *     BlenderType type() const override { return BlenderType::kRuntime; }
 *
 *     bool onAppendStages(const SkStageRec& rec) const override;
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     SK_FLATTENABLE_HOOKS(SkRuntimeBlender)
 *
 *     sk_sp<SkRuntimeEffect> effect() const { return fEffect; }
 *     sk_sp<const SkData> uniforms() const { return fUniforms; }
 *     SkSpan<const SkRuntimeEffect::ChildPtr> children() const { return fChildren; }
 *
 * private:
 *     sk_sp<SkRuntimeEffect> fEffect;
 *     sk_sp<const SkData> fUniforms;
 *     std::vector<SkRuntimeEffect::ChildPtr> fChildren;
 * }
 * ```
 */
public open class SkRuntimeBlender public constructor(
  effect: SkSp<SkRuntimeEffect>,
  uniforms: SkSp<SkData>,
  children: SkSpan<SkRuntimeEffect.ChildPtr>,
) : SkBlenderBase() {
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
   * sk_sp<const SkData> fUniforms
   * ```
   */
  private val fUniforms: SkSp<SkData> = TODO("Initialize fUniforms")

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
   * SkRuntimeEffect* asRuntimeEffect() const override { return fEffect.get(); }
   * ```
   */
  public override fun asRuntimeEffect(): SkRuntimeEffect {
    TODO("Implement asRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * BlenderType type() const override { return BlenderType::kRuntime; }
   * ```
   */
  public override fun type(): BlenderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRuntimeBlender::onAppendStages(const SkStageRec& rec) const {
   *     if (!SkRuntimeEffectPriv::CanDraw(SkCapabilities::RasterBackend().get(), fEffect.get())) {
   *         // SkRP has support for many parts of #version 300 already, but for now, we restrict its
   *         // usage in runtime effects to just #version 100.
   *         return false;
   *     }
   *     if (const SkSL::RP::Program* program = fEffect->getRPProgram(/*debugTrace=*/nullptr)) {
   *         SkSpan<const float> uniforms = SkRuntimeEffectPriv::UniformsAsSpan(
   *                 fEffect->uniforms(),
   *                 fUniforms,
   *                 /*alwaysCopyIntoAlloc=*/false,
   *                 rec.fDstCS,
   *                 rec.fAlloc);
   *         SkShaders::MatrixRec matrix(SkMatrix::I());
   *         matrix.markCTMApplied();
   *         RuntimeEffectRPCallbacks callbacks(rec, matrix, fChildren, fEffect->fSampleUsages);
   *         bool success = program->appendStages(rec.fPipeline, rec.fAlloc, &callbacks, uniforms);
   *         return success;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onAppendStages(rec: SkStageRec): Boolean {
    TODO("Implement onAppendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRuntimeBlender::flatten(SkWriteBuffer& buffer) const {
   *     if (SkKnownRuntimeEffects::IsSkiaKnownRuntimeEffect(fEffect->fStableKey)) {
   *         // We only serialize Skia-internal stableKeys. First party stable keys are not serialized.
   *         buffer.write32(fEffect->fStableKey);
   *     } else {
   *         buffer.write32(0);
   *         buffer.writeString(fEffect->source().c_str());
   *     }
   *     buffer.writeDataAsByteArray(fUniforms.get());
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
   * sk_sp<SkRuntimeEffect> effect() const { return fEffect; }
   * ```
   */
  public fun effect(): SkSp<SkRuntimeEffect> {
    TODO("Implement effect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> uniforms() const { return fUniforms; }
   * ```
   */
  public fun uniforms(): SkSp<SkData> {
    TODO("Implement uniforms")
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
   * sk_sp<SkFlattenable> SkRuntimeBlender::CreateProc(SkReadBuffer& buffer) {
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
   *         effect = SkMakeCachedRuntimeEffect(SkRuntimeEffect::MakeForBlender, std::move(sksl));
   *     }
   *     if constexpr (!kLenientSkSLDeserialization) {
   *         if (!buffer.validate(effect != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     sk_sp<SkData> uniforms = buffer.readByteArrayAsData();
   *
   *     STArray<4, SkRuntimeEffect::ChildPtr> children;
   *     if (!SkRuntimeEffectPriv::ReadChildEffects(buffer, effect.get(), &children)) {
   *         return nullptr;
   *     }
   *
   *     if constexpr (kLenientSkSLDeserialization) {
   *         if (!effect) {
   *             SkDebugf("Serialized SkSL failed to compile. Ignoring/dropping SkSL blender.\n");
   *             return nullptr;
   *         }
   *     }
   *
   *     return effect->makeBlender(std::move(uniforms), SkSpan(children));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
