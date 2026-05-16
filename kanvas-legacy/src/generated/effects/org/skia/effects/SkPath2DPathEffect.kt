package org.skia.effects

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPath2DPathEffect {
 * public:
 *     static sk_sp<SkPathEffect> Make(const SkMatrix& matrix, const SkPath& path);
 *
 *     static void RegisterFlattenables();
 * }
 * ```
 */
public open class SkPath2DPathEffect {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkPath2DPathEffect::Make(const SkMatrix& matrix, const SkPath& path) {
     *     return sk_sp<SkPathEffect>(new SkPath2DPathEffectImpl(matrix, path));
     * }
     * ```
     */
    public fun make(matrix: SkMatrix, path: SkPath): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPath2DPathEffect::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkPath2DPathEffectImpl);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
