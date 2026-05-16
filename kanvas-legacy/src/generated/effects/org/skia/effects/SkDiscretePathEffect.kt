package org.skia.effects

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDiscretePathEffect {
 * public:
 *     /** Break the path into segments of segLength length, and randomly move the endpoints
 *         away from the original path by a maximum of deviation.
 *         Note: works on filled or framed paths
 *
 *         @param seedAssist This is a caller-supplied seedAssist that modifies
 *                           the seed value that is used to randomize the path
 *                           segments' endpoints. If not supplied it defaults to 0,
 *                           in which case filtering a path multiple times will
 *                           result in the same set of segments (this is useful for
 *                           testing). If a caller does not want this behaviour
 *                           they can pass in a different seedAssist to get a
 *                           different set of path segments.
 *     */
 *     static sk_sp<SkPathEffect> Make(SkScalar segLength, SkScalar dev, uint32_t seedAssist = 0);
 *
 *     static void RegisterFlattenables();
 * }
 * ```
 */
public abstract class SkDiscretePathEffect {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkDiscretePathEffect::Make(SkScalar segLength, SkScalar deviation,
     *                                                uint32_t seedAssist) {
     *     if (!SkIsFinite(segLength, deviation)) {
     *         return nullptr;
     *     }
     *     if (segLength <= SK_ScalarNearlyZero) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkPathEffect>(new SkDiscretePathEffectImpl(segLength, deviation, seedAssist));
     * }
     * ```
     */
    public fun make(
      segLength: SkScalar,
      dev: SkScalar,
      seedAssist: UInt = TODO(),
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkDiscretePathEffect::RegisterFlattenables() {
     *     SkFlattenable::Register("SkDiscretePathEffect", SkDiscretePathEffectImpl::CreateProc);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
