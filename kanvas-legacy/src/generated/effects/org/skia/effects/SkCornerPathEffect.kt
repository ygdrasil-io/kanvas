package org.skia.effects

import kotlin.Int
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCornerPathEffect {
 * public:
 *     /** radius must be > 0 to have an effect. It specifies the distance from each corner
 *         that should be "rounded".
 *     */
 *     static sk_sp<SkPathEffect> Make(SkScalar radius);
 *
 *     static void RegisterFlattenables();
 * }
 * ```
 */
public open class SkCornerPathEffect {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkCornerPathEffect::Make(SkScalar radius) {
     *     return SkIsFinite(radius) && (radius > 0) ?
     *             sk_sp<SkPathEffect>(new SkCornerPathEffectImpl(radius)) : nullptr;
     * }
     * ```
     */
    public fun make(radius: SkScalar): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkCornerPathEffect::RegisterFlattenables() {
     *     SkFlattenable::Register("SkCornerPathEffect", SkCornerPathEffectImpl::CreateProc);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
