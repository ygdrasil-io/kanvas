package org.skia.effects

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkLumaColorFilter {
 *     static sk_sp<SkColorFilter> Make();
 * }
 * ```
 */
public open class SkLumaColorFilter {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkLumaColorFilter::Make() {
     *     using namespace SkKnownRuntimeEffects;
     *
     *     const SkRuntimeEffect* lumaEffect = GetKnownRuntimeEffect(StableKey::kLuma);
     *
     *     return lumaEffect->makeColorFilter(SkData::MakeEmpty());
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
