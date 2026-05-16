package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class RuntimeEffectManager {
 * public:
 *     RuntimeEffectManager();
 *
 *     enum class KnownId {
 *         kBlurFilter_MixEffect,
 *         kEdgeExtensionEffect,
 *         kKawaseBlurDualFilter_HighSampleBlurEffect,
 *         kKawaseBlurDualFilter_LowSampleBlurEffect,
 *         kMouriMap_BlurEffect,
 *         kMouriMap_CrossTalkAndChunk16x16Effect,
 *         kMouriMap_Chunk8x8Effect,
 *         kMouriMap_TonemapEffect,
 *     };
 *
 *     sk_sp<SkRuntimeEffect> getKnownRuntimeEffect(KnownId id);
 *
 *     sk_sp<SkRuntimeEffect> getOrCreateLinearRuntimeEffect(const shaders::LinearEffect&);
 *
 * private:
 *     sk_sp<SkRuntimeEffect> fMixEffect;
 *     sk_sp<SkRuntimeEffect> fEdgeExtensionEffect;
 *     sk_sp<SkRuntimeEffect> fKawaseHighSampleEffect;
 *     sk_sp<SkRuntimeEffect> fKawaseLowSampleEffect;
 *     sk_sp<SkRuntimeEffect> fBlurEffect;
 *     sk_sp<SkRuntimeEffect> fCrosstalkAndChunk16x16Effect;
 *     sk_sp<SkRuntimeEffect> fChunk8x8Effect;
 *     sk_sp<SkRuntimeEffect> fToneMapEffect;
 *
 *     std::map<std::string, sk_sp<SkRuntimeEffect>> fLinearEffects;
 * }
 * ```
 */
public data class RuntimeEffectManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fMixEffect
   * ```
   */
  private var fMixEffect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fEdgeExtensionEffect
   * ```
   */
  private var fEdgeExtensionEffect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fKawaseHighSampleEffect
   * ```
   */
  private var fKawaseHighSampleEffect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fKawaseLowSampleEffect
   * ```
   */
  private var fKawaseLowSampleEffect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fBlurEffect
   * ```
   */
  private var fBlurEffect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fCrosstalkAndChunk16x16Effect
   * ```
   */
  private var fCrosstalkAndChunk16x16Effect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fChunk8x8Effect
   * ```
   */
  private var fChunk8x8Effect: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fToneMapEffect
   * ```
   */
  private var fToneMapEffect: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> RuntimeEffectManager::getKnownRuntimeEffect(KnownId id) {
   *
   *     switch (id) {
   *         case KnownId::kBlurFilter_MixEffect:
   *             return fMixEffect;
   *         case KnownId::kEdgeExtensionEffect:
   *             return fEdgeExtensionEffect;
   *         case KnownId::kKawaseBlurDualFilter_HighSampleBlurEffect:
   *             return fKawaseHighSampleEffect;
   *         case KnownId::kKawaseBlurDualFilter_LowSampleBlurEffect:
   *             return fKawaseLowSampleEffect;
   *         case KnownId::kMouriMap_BlurEffect:
   *             return fBlurEffect;
   *         case KnownId::kMouriMap_CrossTalkAndChunk16x16Effect:
   *             return fCrosstalkAndChunk16x16Effect;
   *         case KnownId::kMouriMap_Chunk8x8Effect:
   *             return fChunk8x8Effect;
   *         case KnownId::kMouriMap_TonemapEffect:
   *             return fToneMapEffect;
   *     }
   *
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public fun getKnownRuntimeEffect(id: KnownId): Int {
    TODO("Implement getKnownRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> RuntimeEffectManager::getOrCreateLinearRuntimeEffect(
   *         const shaders::LinearEffect& linearEffect) {
   *
   *     SkString name = SkStringPrintf("RE_LinearEffect_%s__%s__%s__%s__%s",
   *                                    to_str(linearEffect.inputDataspace).c_str(),
   *                                    to_str(linearEffect.outputDataspace).c_str(),
   *                                    linearEffect.undoPremultipliedAlpha ? "true" : "false",
   *                                    to_str(linearEffect.fakeOutputDataspace).c_str(),
   *                                    to_str(linearEffect.type));
   *
   *     auto result = fLinearEffects.find(name.c_str());
   *     if (result != fLinearEffects.end()) {
   *         return result->second;
   *     }
   *
   *     // Each code snippet must be unique, otherwise Skia will internally find a match
   *     // and uniquify things. To avoid this we just add an arbitrary alpha constant
   *     // to the code.
   *     static float arbitraryAlpha = 0.051f;
   *     SkString linearEffectCode = SkStringPrintf(
   *             "uniform shader child;"
   *             "vec4 main(vec2 xy) {"
   *                 "float3 linear = toLinearSrgb(child.eval(xy).rgb);"
   *                 "return float4(fromLinearSrgb(linear), %f);"
   *             "}",
   *             arbitraryAlpha);
   *     arbitraryAlpha += 0.05f;
   *
   *     sk_sp<SkRuntimeEffect> effect = makeEffect(linearEffectCode, name.c_str());
   *     fLinearEffects.insert({ name.c_str(), effect });
   *     return effect;
   * }
   * ```
   */
  public fun getOrCreateLinearRuntimeEffect(linearEffect: LinearEffect): Int {
    TODO("Implement getOrCreateLinearRuntimeEffect")
  }

  public enum class KnownId {
    kBlurFilter_MixEffect,
    kEdgeExtensionEffect,
    kKawaseBlurDualFilter_HighSampleBlurEffect,
    kKawaseBlurDualFilter_LowSampleBlurEffect,
    kMouriMap_BlurEffect,
    kMouriMap_CrossTalkAndChunk16x16Effect,
    kMouriMap_Chunk8x8Effect,
    kMouriMap_TonemapEffect,
  }
}
