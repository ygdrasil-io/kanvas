package org.skia.effects

import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkColor

/**
 * C++ original:
 * ```cpp
 * class SK_API SkOverdrawColorFilter {
 * public:
 *     static constexpr int kNumColors = 6;
 *
 *     static sk_sp<SkColorFilter> MakeWithSkColors(const SkColor[kNumColors]);
 * }
 * ```
 */
public open class SkOverdrawColorFilter {
  public companion object {
    public val kNumColors: Int = TODO("Initialize kNumColors")

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkOverdrawColorFilter::MakeWithSkColors(const SkColor colors[kNumColors]) {
     *     using namespace SkKnownRuntimeEffects;
     *
     *     const SkRuntimeEffect* overdrawEffect = GetKnownRuntimeEffect(StableKey::kOverdraw);
     *
     *     auto data = SkData::MakeUninitialized(kNumColors * sizeof(SkPMColor4f));
     *     SkPMColor4f* premul = (SkPMColor4f*)data->writable_data();
     *     for (int i = 0; i < kNumColors; ++i) {
     *         premul[i] = SkColor4f::FromColor(colors[i]).premul();
     *     }
     *     return overdrawEffect->makeColorFilter(std::move(data));
     * }
     * ```
     */
    public fun makeWithSkColors(colors: Array<SkColor>): Int {
      TODO("Implement makeWithSkColors")
    }
  }
}
