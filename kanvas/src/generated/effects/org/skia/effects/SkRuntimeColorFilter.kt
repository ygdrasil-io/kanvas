package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStageRec
import org.skia.foundation.SkData
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkRuntimeColorFilter : public SkColorFilterBase {
 * public:
 *     SkRuntimeColorFilter(sk_sp<SkRuntimeEffect> effect,
 *                          sk_sp<const SkData> uniforms,
 *                          SkSpan<const SkRuntimeEffect::ChildPtr> children);
 *
 *     bool appendStages(const SkStageRec& rec, bool) const override;
 *
 *     bool onIsAlphaUnchanged() const override;
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     SkRuntimeEffect* asRuntimeEffect() const override;
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kRuntime; }
 *
 *     SK_FLATTENABLE_HOOKS(SkRuntimeColorFilter)
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
public open class SkRuntimeColorFilter public constructor(
  effect: SkSp<SkRuntimeEffect>,
  uniforms: SkSp<SkData>,
  children: SkSpan<SkRuntimeEffect.ChildPtr>,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> fUniforms
   * ```
   */
  private var fUniforms: Int = TODO("Initialize fUniforms")

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
   * bool SkRuntimeColorFilter::appendStages(const SkStageRec& rec, bool) const {
   *     if (!SkRuntimeEffectPriv::CanDraw(SkCapabilities::RasterBackend().get(), fEffect.get())) {
   *         // SkRP has support for many parts of #version 300 already, but for now, we restrict its
   *         // usage in runtime effects to just #version 100.
   *         return false;
   *     }
   *     if (const SkSL::RP::Program* program = fEffect->getRPProgram(/*debugTrace=*/nullptr)) {
   *         SkSpan<const float> uniforms =
   *                 SkRuntimeEffectPriv::UniformsAsSpan(fEffect->uniforms(),
   *                                                     fUniforms,
   *                                                     /*alwaysCopyIntoAlloc=*/false,
   *                                                     rec.fDstCS,
   *                                                     rec.fAlloc);
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
  public override fun appendStages(rec: SkStageRec, param1: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRuntimeColorFilter::onIsAlphaUnchanged() const {
   *     return fEffect->isAlphaUnchanged();
   * }
   * ```
   */
  public override fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRuntimeColorFilter::flatten(SkWriteBuffer& buffer) const {
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
   * SkRuntimeEffect* SkRuntimeColorFilter::asRuntimeEffect() const { return fEffect.get(); }
   * ```
   */
  public override fun asRuntimeEffect(): Int {
    TODO("Implement asRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kRuntime; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkRuntimeColorFilter::CreateProc(SkReadBuffer& buffer) {
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
   *         effect = SkMakeCachedRuntimeEffect(SkRuntimeEffect::MakeForColorFilter, std::move(sksl));
   *     }
   *     if constexpr (!kLenientSkSLDeserialization) {
   *         if (!buffer.validate(effect != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     sk_sp<SkData> uniforms = buffer.readByteArrayAsData();
   *
   *     skia_private::STArray<4, SkRuntimeEffect::ChildPtr> children;
   *     if (!SkRuntimeEffectPriv::ReadChildEffects(buffer, effect.get(), &children)) {
   *         return nullptr;
   *     }
   *
   *     if constexpr (kLenientSkSLDeserialization) {
   *         if (!effect) {
   *             SkDebugf("Serialized SkSL failed to compile. Ignoring/dropping SkSL color filter.\n");
   *             return nullptr;
   *         }
   *     }
   *
   *     return effect->makeColorFilter(std::move(uniforms), SkSpan(children));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
