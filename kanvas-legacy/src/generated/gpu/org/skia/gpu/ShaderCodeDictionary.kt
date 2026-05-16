package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class ShaderCodeDictionary {
 * public:
 *     ShaderCodeDictionary(Layout layout,
 *                          SkSpan<sk_sp<SkRuntimeEffect>> userDefinedKnownRuntimeEffects);
 *
 *     UniquePaintParamsID findOrCreate(const PaintParamsKey&);
 *
 *     UniquePaintParamsID findOrCreate(PaintParamsKeyBuilder*) SK_EXCLUDES(fSpinLock);
 *
 *     PaintParamsKey lookup(UniquePaintParamsID) const SK_EXCLUDES(fSpinLock);
 *
 *     SkString idToString(const Caps* caps, UniquePaintParamsID id) const {
 *         return this->lookup(id).toString(caps, this);
 *     }
 *
 * #if defined(SK_DEBUG)
 *     bool isValidID(int snippetID) const SK_EXCLUDES(fSpinLock);
 *
 *     void dump(const Caps*, UniquePaintParamsID) const;
 * #endif
 *
 *     // This method can return nullptr
 *     const ShaderSnippet* getEntry(int codeSnippetID) const SK_EXCLUDES(fSpinLock);
 *     const ShaderSnippet* getEntry(BuiltInCodeSnippetID codeSnippetID) const {
 *         // Built-in code snippets are initialized once so there is no need to take a lock
 *         return &fBuiltInCodeSnippets[SkTo<int>(codeSnippetID)];
 *     }
 *
 *     // getEntry can be used to retrieve the ShaderSnippet for a user-defined known runtime effect
 *     // but, since the ShaderCodeDictionary owns those runtime effects, we need another entry
 *     // point to retrieve the actual effect. For unknown runtime effects this is handled by the
 *     // RuntimeEffectDictionary which, transiently, holds a ref on the encountered runtime effects.
 *     const SkRuntimeEffect* getUserDefinedKnownRuntimeEffect(int codeSnippetID) const;
 *
 *     // Returns -1 on failure
 *     int findOrCreateRuntimeEffectSnippet(const SkRuntimeEffect* effect) SK_EXCLUDES(fSpinLock);
 *
 *     bool isUserDefinedKnownRuntimeEffect(int candidate) const;
 * #if defined(GPU_TEST_UTILS)
 *     int numUserDefinedRuntimeEffects() const SK_EXCLUDES(fSpinLock);
 *     int numUserDefinedKnownRuntimeEffects() const;
 * #endif
 *
 * private:
 *     const char* addTextToArena(std::string_view text);
 *
 *     SkSpan<const Uniform> convertUniforms(const SkRuntimeEffect* effect);
 *     ShaderSnippet convertRuntimeEffect(const SkRuntimeEffect* effect, const char* name);
 *
 *     void registerUserDefinedKnownRuntimeEffects(SkSpan<sk_sp<SkRuntimeEffect>>);
 *
 *     const Layout fLayout;
 *
 *     std::array<ShaderSnippet, kBuiltInCodeSnippetIDCount> fBuiltInCodeSnippets;
 *
 *     using KnownRuntimeEffectArray = std::array<ShaderSnippet, SkKnownRuntimeEffects::kStableKeyCnt>;
 *     KnownRuntimeEffectArray fKnownRuntimeEffectCodeSnippets SK_GUARDED_BY(fSpinLock);
 *
 *     using ShaderSnippetArray = skia_private::TArray<ShaderSnippet>;
 *     using RuntimeEffectArray = skia_private::TArray<sk_sp<SkRuntimeEffect>>;
 *
 *     // These two arrays are not guarded by a lock since they are only initialized in the ctor
 *     ShaderSnippetArray fUserDefinedKnownCodeSnippets;
 *     RuntimeEffectArray fUserDefinedKnownRuntimeEffects;
 *
 *     // The value returned from 'getEntry' must be stable so, hold the user-defined code snippet
 *     // entries as pointers.
 *     ShaderSnippetArray fUserDefinedCodeSnippets SK_GUARDED_BY(fSpinLock);
 *
 *     // TODO: can we do something better given this should have write-seldom/read-often behavior?
 *     mutable SkSpinlock fSpinLock;
 *
 *     using PaintIDMap = skia_private::THashMap<PaintParamsKey,
 *                                               UniquePaintParamsID,
 *                                               PaintParamsKey::Hash>;
 *
 *     PaintIDMap fPaintKeyToID SK_GUARDED_BY(fSpinLock);
 *     skia_private::TArray<PaintParamsKey> fIDToPaintKey SK_GUARDED_BY(fSpinLock);
 *
 *     SK_BEGIN_REQUIRE_DENSE
 *     struct RuntimeEffectKey {
 *         uint32_t fHash;
 *         uint32_t fUniformSize;
 *
 *         bool operator==(RuntimeEffectKey rhs) const {
 *             return fHash == rhs.fHash && fUniformSize == rhs.fUniformSize;
 *         }
 *     };
 *     SK_END_REQUIRE_DENSE
 *
 *     // A map from RuntimeEffectKeys (hash plus uniforms) to code-snippet IDs. RuntimeEffectKeys
 *     // don't track the lifetime of a runtime effect at all; they live forever, and a newly-
 *     // instantiated runtime effect with the same program as a previously-discarded effect will reuse
 *     // an existing ID. Entries in the runtime-effect map are never removed; they only disappear when
 *     // the context is discarded, which takes the ShaderCodeDictionary along with it. However, they
 *     // are extremely small (< 20 bytes) so the memory footprint should be unnoticeable.
 *     using RuntimeEffectMap = skia_private::THashMap<RuntimeEffectKey, int32_t>;
 *     RuntimeEffectMap fRuntimeEffectMap SK_GUARDED_BY(fSpinLock);
 *
 *     // This arena holds:
 *     //   - the backing data for PaintParamsKeys in `fPaintKeyToID` and `fIDToPaintKey`
 *     //   - Uniform data created by `findOrCreateRuntimeEffectSnippet`
 *     // and in all cases is guarded by `fSpinLock`
 *     SkArenaAlloc fArena{256};
 * }
 * ```
 */
public data class ShaderCodeDictionary public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Layout fLayout
   * ```
   */
  private val fLayout: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<ShaderSnippet, kBuiltInCodeSnippetIDCount> fBuiltInCodeSnippets
   * ```
   */
  private var fBuiltInCodeSnippets: Int,
  /**
   * C++ original:
   * ```cpp
   * KnownRuntimeEffectArray fKnownRuntimeEffectCodeSnippets
   * ```
   */
  private var fKnownRuntimeEffectCodeSnippets: Int,
  /**
   * C++ original:
   * ```cpp
   * ShaderSnippetArray fUserDefinedKnownCodeSnippets
   * ```
   */
  private var fUserDefinedKnownCodeSnippets: Int,
  /**
   * C++ original:
   * ```cpp
   * RuntimeEffectArray fUserDefinedKnownRuntimeEffects
   * ```
   */
  private var fUserDefinedKnownRuntimeEffects: Int,
  /**
   * C++ original:
   * ```cpp
   * ShaderSnippetArray fUserDefinedCodeSnippets
   * ```
   */
  private var fUserDefinedCodeSnippets: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fSpinLock: Int,
  /**
   * C++ original:
   * ```cpp
   * PaintIDMap fPaintKeyToID
   * ```
   */
  private var fPaintKeyToID: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<PaintParamsKey> fIDToPaintKey
   * ```
   */
  private var fIDToPaintKey: Int,
  /**
   * C++ original:
   * ```cpp
   * RuntimeEffectMap fRuntimeEffectMap
   * ```
   */
  private var fRuntimeEffectMap: Int,
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc fArena
   * ```
   */
  private var fArena: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID findOrCreate(const PaintParamsKey&)
   * ```
   */
  public fun findOrCreate(param0: PaintParamsKey): Int {
    TODO("Implement findOrCreate")
  }

  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID findOrCreate(PaintParamsKeyBuilder*)
   * ```
   */
  public fun findOrCreate(param0: PaintParamsKeyBuilder?): Int {
    TODO("Implement findOrCreate")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKey ShaderCodeDictionary::lookup(UniquePaintParamsID codeID) const {
   *     if (!codeID.isValid()) {
   *         return PaintParamsKey::Invalid();
   *     }
   *
   *     SkAutoSpinlock lock{fSpinLock};
   *     SkASSERT(codeID.asUInt() < SkTo<uint32_t>(fIDToPaintKey.size()));
   *     return fIDToPaintKey[codeID.asUInt()];
   * }
   * ```
   */
  public fun lookup(codeID: UniquePaintParamsID): Int {
    TODO("Implement lookup")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString idToString(const Caps* caps, UniquePaintParamsID id) const {
   *         return this->lookup(id).toString(caps, this);
   *     }
   * ```
   */
  public fun idToString(caps: Caps?, id: UniquePaintParamsID): Int {
    TODO("Implement idToString")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderSnippet* ShaderCodeDictionary::getEntry(int codeSnippetID) const {
   *     if (codeSnippetID < 0) {
   *         return nullptr;
   *     }
   *
   *     if (codeSnippetID < kBuiltInCodeSnippetIDCount) {
   *         return &fBuiltInCodeSnippets[codeSnippetID];
   *     }
   *
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     if (IsSkiaKnownRuntimeEffect(codeSnippetID)) {
   *         int knownRTECodeSnippetID = codeSnippetID - kSkiaKnownRuntimeEffectsStart;
   *
   *         // TODO(b/238759147): if the snippet hasn't been initialized, get the SkRuntimeEffect and
   *         // initialize it here
   *         SkASSERT(fKnownRuntimeEffectCodeSnippets[knownRTECodeSnippetID].fPreambleGenerator);
   *         return &fKnownRuntimeEffectCodeSnippets[knownRTECodeSnippetID];
   *     }
   *
   *     if (IsViableUserDefinedKnownRuntimeEffect(codeSnippetID)) {
   *         int index = codeSnippetID - kUserDefinedKnownRuntimeEffectsStart;
   *         if (index >= fUserDefinedKnownCodeSnippets.size()) {
   *             return nullptr;
   *         }
   *
   *         SkASSERT(fUserDefinedKnownCodeSnippets[index].fPreambleGenerator);
   *         return &fUserDefinedKnownCodeSnippets[index];
   *     }
   *
   *     if (IsUserDefinedRuntimeEffect(codeSnippetID)) {
   *         int userDefinedCodeSnippetID = codeSnippetID - kUnknownRuntimeEffectIDStart;
   *         if (userDefinedCodeSnippetID < SkTo<int>(fUserDefinedCodeSnippets.size())) {
   *             return &fUserDefinedCodeSnippets[userDefinedCodeSnippetID];
   *         }
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun getEntry(codeSnippetID: Int): ShaderSnippet {
    TODO("Implement getEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderSnippet* getEntry(BuiltInCodeSnippetID codeSnippetID) const {
   *         // Built-in code snippets are initialized once so there is no need to take a lock
   *         return &fBuiltInCodeSnippets[SkTo<int>(codeSnippetID)];
   *     }
   * ```
   */
  public fun getEntry(codeSnippetID: BuiltInCodeSnippetID): ShaderSnippet {
    TODO("Implement getEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRuntimeEffect* ShaderCodeDictionary::getUserDefinedKnownRuntimeEffect(
   *         int codeSnippetID) const {
   *     if (codeSnippetID < 0) {
   *         return nullptr;
   *     }
   *
   *     if (IsViableUserDefinedKnownRuntimeEffect(codeSnippetID)) {
   *         int index = codeSnippetID - kUserDefinedKnownRuntimeEffectsStart;
   *         if (index >= fUserDefinedKnownRuntimeEffects.size()) {
   *             return nullptr;
   *         }
   *
   *         SkASSERT(fUserDefinedKnownRuntimeEffects[index]);
   *         return fUserDefinedKnownRuntimeEffects[index].get();
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun getUserDefinedKnownRuntimeEffect(codeSnippetID: Int): SkRuntimeEffect {
    TODO("Implement getUserDefinedKnownRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * int ShaderCodeDictionary::findOrCreateRuntimeEffectSnippet(const SkRuntimeEffect* effect) {
   *      SkAutoSpinlock lock{fSpinLock};
   *
   *     if (int stableKey = SkRuntimeEffectPriv::StableKey(*effect)) {
   *         if (IsSkiaKnownRuntimeEffect(stableKey)) {
   *             int index = stableKey - kSkiaKnownRuntimeEffectsStart;
   *
   *             if (!fKnownRuntimeEffectCodeSnippets[index].fPreambleGenerator) {
   *                 const char* name = get_known_rte_name(static_cast<StableKey>(stableKey));
   *                 fKnownRuntimeEffectCodeSnippets[index] = this->convertRuntimeEffect(effect, name);
   *             }
   *
   *             return stableKey;
   *         } else if (IsViableUserDefinedKnownRuntimeEffect(stableKey)) {
   *             int index = stableKey - kUserDefinedKnownRuntimeEffectsStart;
   *             if (index >= fUserDefinedKnownCodeSnippets.size()) {
   *                 return -1;
   *             }
   *
   *             return stableKey;
   *         }
   *
   *         return -1;
   *     }
   *
   *     // Use the combination of {SkSL program hash, uniform size} as our key.
   *     // In the unfortunate event of a hash collision, at least we'll have the right amount of
   *     // uniform data available.
   *     RuntimeEffectKey key;
   *     key.fHash = SkRuntimeEffectPriv::Hash(*effect);
   *     key.fUniformSize = effect->uniformSize();
   *
   *     int32_t* existingCodeSnippetID = fRuntimeEffectMap.find(key);
   *     if (existingCodeSnippetID) {
   *         return *existingCodeSnippetID;
   *     }
   *
   *     // TODO: the memory for user-defined entries could go in the dictionary's arena but that
   *     // would have to be a thread safe allocation since the arena also stores entries for
   *     // 'fHash' and 'fEntryVector'
   *     static const char* kDefaultName = "RuntimeEffect";
   *     fUserDefinedCodeSnippets.push_back(this->convertRuntimeEffect(
   *                 effect,
   *                 SkRuntimeEffectPriv::HasName(*effect) ? SkRuntimeEffectPriv::GetName(*effect)
   *                                                       : kDefaultName));
   *     int newCodeSnippetID = kUnknownRuntimeEffectIDStart + fUserDefinedCodeSnippets.size() - 1;
   *
   *     fRuntimeEffectMap.set(key, newCodeSnippetID);
   *     return newCodeSnippetID;
   * }
   * ```
   */
  public fun findOrCreateRuntimeEffectSnippet(effect: SkRuntimeEffect?): Int {
    TODO("Implement findOrCreateRuntimeEffectSnippet")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShaderCodeDictionary::isUserDefinedKnownRuntimeEffect(int candidate) const {
   *     if (!SkKnownRuntimeEffects::IsViableUserDefinedKnownRuntimeEffect(candidate)) {
   *         return false;
   *     }
   *
   *     int index = candidate - kUserDefinedKnownRuntimeEffectsStart;
   *     if (index >= fUserDefinedKnownCodeSnippets.size()) {
   *         return false;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun isUserDefinedKnownRuntimeEffect(candidate: Int): Boolean {
    TODO("Implement isUserDefinedKnownRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * int ShaderCodeDictionary::numUserDefinedRuntimeEffects() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return fUserDefinedCodeSnippets.size();
   * }
   * ```
   */
  public fun numUserDefinedRuntimeEffects(): Int {
    TODO("Implement numUserDefinedRuntimeEffects")
  }

  /**
   * C++ original:
   * ```cpp
   * int ShaderCodeDictionary::numUserDefinedKnownRuntimeEffects() const {
   *     return fUserDefinedKnownCodeSnippets.size();
   * }
   * ```
   */
  public fun numUserDefinedKnownRuntimeEffects(): Int {
    TODO("Implement numUserDefinedKnownRuntimeEffects")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* ShaderCodeDictionary::addTextToArena(std::string_view text) {
   *     char* textInArena = fArena.makeArrayDefault<char>(text.size() + 1);
   *     memcpy(textInArena, text.data(), text.size());
   *     textInArena[text.size()] = '\0';
   *     return textInArena;
   * }
   * ```
   */
  private fun addTextToArena(text: String): Char {
    TODO("Implement addTextToArena")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Uniform> ShaderCodeDictionary::convertUniforms(const SkRuntimeEffect* effect) {
   *     using rteUniform = SkRuntimeEffect::Uniform;
   *     SkSpan<const rteUniform> uniforms = effect->uniforms();
   *
   *     const int numUniforms = uniforms.size();
   *
   *     // Convert the SkRuntimeEffect::Uniform array into its Uniform equivalent.
   *     Uniform* uniformArray = fArena.makeInitializedArray<Uniform>(numUniforms, [&](int index) {
   *         const rteUniform* u;
   *         u = &uniforms[index];
   *
   *         // The existing uniform names live in the passed-in SkRuntimeEffect and may eventually
   *         // disappear. Copy them into fArena. (It's safe to do this within makeInitializedArray; the
   *         // entire array is allocated in one big slab before any initialization calls are done.)
   *         const char* name = this->addTextToArena(u->name);
   *
   *         // Add one Uniform to our array.
   *         SkSLType type = uniform_type_to_sksl_type(*u);
   *         return (u->flags & rteUniform::kArray_Flag) ? Uniform(name, type, u->count)
   *                                                     : Uniform(name, type);
   *     });
   *
   *     return SkSpan<const Uniform>(uniformArray, numUniforms);
   * }
   * ```
   */
  private fun convertUniforms(effect: SkRuntimeEffect?): Int {
    TODO("Implement convertUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderSnippet ShaderCodeDictionary::convertRuntimeEffect(const SkRuntimeEffect* effect,
   *                                                          const char* name) {
   *     SkEnumBitMask<SnippetRequirementFlags> snippetFlags = SnippetRequirementFlags::kNone;
   *     if (effect->allowShader()) {
   *         // TODO(b/412621191) Ideally we would have a way to tell exactly which children of a runtime
   *         // shader are sampled with modified coords, or whether coordinates are required at all. For
   *         // now we assume all runtime shaders need coordinates, and if any children are sampled with
   *         // modified coords, we assume they all are.
   *         snippetFlags |= SnippetRequirementFlags::kLocalCoords;
   *         if (all_sample_usages_are_passthrough(effect)) {
   *             snippetFlags |= SnippetRequirementFlags::kPassthroughLocalCoords;
   *         }
   *     } else if (effect->allowColorFilter()) {
   *         snippetFlags |= SnippetRequirementFlags::kPriorStageOutput;
   *     } else if (effect->allowBlender()) {
   *         snippetFlags |= SnippetRequirementFlags::kPriorStageOutput; // src
   *         snippetFlags |= SnippetRequirementFlags::kBlenderDstColor;  // dst
   *     }
   *
   *     // If the runtime effect references toLinearSrgb() or fromLinearSrgb(), we append two
   *     // color space transform children that are invoked when converting those "built-in" expressions.
   *     int numChildrenIncColorTransforms = SkTo<int>(effect->children().size()) +
   *                                         (SkRuntimeEffectPriv::UsesColorTransform(effect) ? 2 : 0);
   *
   *     // TODO: We can have the custom runtime effect preamble generator define structs for its
   *     // uniforms if it has a lot of uniforms, and then calculate the required alignment here.
   *     return ShaderSnippet(name,
   *                          /*staticFn=*/nullptr,
   *                          snippetFlags,
   *                          this->convertUniforms(effect),
   *                          /*texturesAndSamplers=*/{},
   *                          GenerateRuntimeShaderPreamble,
   *                          numChildrenIncColorTransforms);
   * }
   * ```
   */
  private fun convertRuntimeEffect(effect: SkRuntimeEffect?, name: String?): ShaderSnippet {
    TODO("Implement convertRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShaderCodeDictionary::registerUserDefinedKnownRuntimeEffects(
   *         SkSpan<sk_sp<SkRuntimeEffect>> userDefinedKnownRuntimeEffects) {
   *     // This is a formality to guard 'fRuntimeEffectMap'. This method should only be called by
   *     // the constructor.
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     for (const sk_sp<SkRuntimeEffect>& u : userDefinedKnownRuntimeEffects) {
   *         if (!u) {
   *             continue;
   *         }
   *
   *         if (fUserDefinedKnownCodeSnippets.size() >= kUserDefinedKnownRuntimeEffectsReservedCnt) {
   *             SKGPU_LOG_W("Too many user-defined known runtime effects. Only %d out of %zu "
   *                         "will be known.\n",
   *                         kUserDefinedKnownRuntimeEffectsReservedCnt,
   *                         userDefinedKnownRuntimeEffects.size());
   *             // too many user-defined known runtime effects
   *             return;
   *         }
   *
   *         RuntimeEffectKey key;
   *         key.fHash = SkRuntimeEffectPriv::Hash(*u);
   *         key.fUniformSize = u->uniformSize();
   *
   *         int32_t* existingCodeSnippetID = fRuntimeEffectMap.find(key);
   *         if (existingCodeSnippetID) {
   *             continue;           // This is a duplicate
   *         }
   *
   *         static const char* kDefaultName = "UserDefinedKnownRuntimeEffect";
   *         fUserDefinedKnownCodeSnippets.push_back(this->convertRuntimeEffect(
   *                     u.get(),
   *                     SkRuntimeEffectPriv::HasName(*u) ? SkRuntimeEffectPriv::GetName(*u)
   *                                                      : kDefaultName));
   *         int stableID = kUserDefinedKnownRuntimeEffectsStart +
   *                        fUserDefinedKnownCodeSnippets.size() - 1;
   *
   *         SkRuntimeEffectPriv::SetStableKey(u.get(), stableID);
   *
   *         fUserDefinedKnownRuntimeEffects.push_back(u);
   *
   *         // We register the key with the runtime effect map so that, if the user uses the same code
   *         // in a separate runtime effect (which they should *not* do), it will be discovered during
   *         // the unknown-runtime-effect processing and mapped back to the registered user-defined
   *         // known runtime effect.
   *         fRuntimeEffectMap.set(key, stableID);
   *     }
   *
   *     SkASSERT(fUserDefinedKnownCodeSnippets.size() == fUserDefinedKnownRuntimeEffects.size());
   * }
   * ```
   */
  private fun registerUserDefinedKnownRuntimeEffects(userDefinedKnownRuntimeEffects: SkSpan<SkSp<SkRuntimeEffect>>) {
    TODO("Implement registerUserDefinedKnownRuntimeEffects")
  }

  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID ShaderCodeDictionary::findOrCreate(PaintParamsKeyBuilder* builder) {
   *     AutoLockBuilderAsKey keyView{builder};
   *
   *     return this->findOrCreate(*keyView);
   * }
   * ```
   */
  public fun isValidID(snippetID: Int): Boolean {
    TODO("Implement isValidID")
  }

  /**
   * C++ original:
   * ```cpp
   * UniquePaintParamsID ShaderCodeDictionary::findOrCreate(const PaintParamsKey& ppk) {
   *     if (!ppk.isValid()) {
   *         return UniquePaintParamsID::Invalid();
   *     }
   *
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     UniquePaintParamsID* existingEntry = fPaintKeyToID.find(ppk);
   *     if (existingEntry) {
   *         SkASSERT(fIDToPaintKey[(*existingEntry).asUInt()] == ppk);
   *         return *existingEntry;
   *     }
   *
   *     // Detach from the builder and copy into the arena
   *     PaintParamsKey key = ppk.clone(&fArena);
   *     UniquePaintParamsID newID{SkTo<uint32_t>(fIDToPaintKey.size())};
   *
   *     fPaintKeyToID.set(key, newID);
   *     fIDToPaintKey.push_back(key);
   *     return newID;
   * }
   * ```
   */
  public fun dump(caps: Caps?, id: UniquePaintParamsID) {
    TODO("Implement dump")
  }

  public data class RuntimeEffectKey public constructor(
    public var fHash: Int,
    public var fUniformSize: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}
