package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class RuntimeEffectDictionary : public SkRefCnt {
 * public:
 *     const SkRuntimeEffect* find(int codeSnippetID) const {
 *         sk_sp<const SkRuntimeEffect>* effect = fDict.find(codeSnippetID);
 *         return effect ? effect->get() : nullptr;
 *     }
 *
 *     void set(int codeSnippetID, sk_sp<const SkRuntimeEffect> effect);
 *
 *     bool empty() const { return fDict.empty(); }
 *
 * private:
 *     skia_private::THashMap<int, sk_sp<const SkRuntimeEffect>> fDict;
 * }
 * ```
 */
public open class RuntimeEffectDictionary : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<int, sk_sp<const SkRuntimeEffect>> fDict
   * ```
   */
  private var fDict: Int = TODO("Initialize fDict")

  /**
   * C++ original:
   * ```cpp
   * const SkRuntimeEffect* find(int codeSnippetID) const {
   *         sk_sp<const SkRuntimeEffect>* effect = fDict.find(codeSnippetID);
   *         return effect ? effect->get() : nullptr;
   *     }
   * ```
   */
  public fun find(codeSnippetID: Int): SkRuntimeEffect {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void RuntimeEffectDictionary::set(int codeSnippetID, sk_sp<const SkRuntimeEffect> effect) {
   *     // The same code-snippet ID should never refer to two different effects.
   *     SkASSERT(!fDict.find(codeSnippetID) || (SkRuntimeEffectPriv::Hash(*fDict[codeSnippetID]) ==
   *                                             SkRuntimeEffectPriv::Hash(*effect)));
   *     fDict.set(codeSnippetID, std::move(effect));
   * }
   * ```
   */
  public fun `set`(codeSnippetID: Int, effect: SkSp<SkRuntimeEffect>) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fDict.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }
}
