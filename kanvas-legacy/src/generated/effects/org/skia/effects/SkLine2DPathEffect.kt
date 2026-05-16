package org.skia.effects

import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkLine2DPathEffect {
 * public:
 *     static sk_sp<SkPathEffect> Make(SkScalar width, const SkMatrix& matrix);
 *
 *     static void RegisterFlattenables();
 * }
 * ```
 */
public open class SkLine2DPathEffect {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkLine2DPathEffect::Make(SkScalar width, const SkMatrix& matrix) {
     *     if (!(width >= 0)) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkPathEffect>(new SkLine2DPathEffectImpl(width, matrix));
     * }
     * ```
     */
    public fun make(width: SkScalar, matrix: SkMatrix): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkLine2DPathEffect::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkLine2DPathEffectImpl);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
