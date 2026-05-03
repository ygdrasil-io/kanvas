package org.skia.effects

import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkBlenders {
 * public:
 *     /**
 *      *  Create a blender that implements the following:
 *      *     k1 * src * dst + k2 * src + k3 * dst + k4
 *      *  @param k1, k2, k3, k4 The four coefficients.
 *      *  @param enforcePMColor If true, the RGB channels will be clamped to the calculated alpha.
 *      */
 *     static sk_sp<SkBlender> Arithmetic(float k1, float k2, float k3, float k4, bool enforcePremul);
 *
 * private:
 *     SkBlenders() = delete;
 * }
 * ```
 */
public open class SkBlenders public constructor() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkBlender> SkBlenders::Arithmetic(float k1, float k2, float k3, float k4,
     *                                         bool enforcePremul) {
     *     using namespace SkKnownRuntimeEffects;
     *
     *     if (!SkIsFinite(k1, k2, k3, k4)) {
     *         return nullptr;
     *     }
     *
     *     // Are we nearly a SkBlendMode?
     *     const struct {
     *         float       k1, k2, k3, k4;
     *         SkBlendMode mode;
     *     } table[] = {
     *         { 0, 1, 0, 0, SkBlendMode::kSrc   },
     *         { 0, 0, 1, 0, SkBlendMode::kDst   },
     *         { 0, 0, 0, 0, SkBlendMode::kClear },
     *     };
     *     for (const auto& t : table) {
     *         if (SkScalarNearlyEqual(k1, t.k1) &&
     *             SkScalarNearlyEqual(k2, t.k2) &&
     *             SkScalarNearlyEqual(k3, t.k3) &&
     *             SkScalarNearlyEqual(k4, t.k4)) {
     *             return SkBlender::Mode(t.mode);
     *         }
     *     }
     *
     *     // If we get here, we need the actual blender effect.
     *     const SkRuntimeEffect* arithmeticEffect = GetKnownRuntimeEffect(StableKey::kArithmetic);
     *
     *     const float array[] = {
     *         k1, k2, k3, k4,
     *         enforcePremul ? 0.0f : 1.0f,
     *     };
     *     return arithmeticEffect->makeBlender(SkData::MakeWithCopy(array, sizeof(array)));
     * }
     * ```
     */
    public fun arithmetic(
      k1: Float,
      k2: Float,
      k3: Float,
      k4: Float,
      enforcePremul: Boolean,
    ): Int {
      TODO("Implement arithmetic")
    }
  }
}
