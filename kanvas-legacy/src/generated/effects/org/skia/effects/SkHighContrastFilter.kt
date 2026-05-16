package org.skia.effects

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkHighContrastFilter {
 *     // Returns the filter, or nullptr if the config is invalid.
 *     static sk_sp<SkColorFilter> Make(const SkHighContrastConfig& config);
 * }
 * ```
 */
public open class SkHighContrastFilter {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkHighContrastFilter::Make(const SkHighContrastConfig& config) {
     *     if (!config.isValid()) {
     *         return nullptr;
     *     }
     *
     *     struct Uniforms { float grayscale, invertStyle, contrast; };
     *
     *     // A contrast setting of exactly +1 would divide by zero (1+c)/(1-c), so pull in to +1-ε.
     *     // I'm not exactly sure why we've historically pinned -1 up to -1+ε, maybe just symmetry?
     *     float c = SkTPin(config.fContrast,
     *                      -1.0f + FLT_EPSILON,
     *                      +1.0f - FLT_EPSILON);
     *
     *     Uniforms uniforms = {
     *         config.fGrayscale ? 1.0f : 0.0f,
     *         (float)config.fInvertStyle,  // 0.0f for none, 1.0f for brightness, 2.0f for lightness
     *         (1+c)/(1-c),
     *     };
     *
     *     const SkRuntimeEffect* highContrastEffect =
     *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kHighContrast);
     *
     *     const SkAlphaType kUnpremul = kUnpremul_SkAlphaType;
     *     return SkColorFilterPriv::WithWorkingFormat(
     *             highContrastEffect->makeColorFilter(SkData::MakeWithCopy(&uniforms,sizeof(uniforms))),
     *             &SkNamedTransferFn::kLinear,
     *             /* gamut= */ nullptr,           // use the dst gamut
     *             &kUnpremul);
     * }
     * ```
     */
    public fun make(config: SkHighContrastConfig): Int {
      TODO("Implement make")
    }
  }
}
