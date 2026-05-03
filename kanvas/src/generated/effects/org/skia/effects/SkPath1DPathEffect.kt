package org.skia.effects

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPath1DPathEffect {
 * public:
 *     enum Style {
 *         kTranslate_Style,   // translate the shape to each position
 *         kRotate_Style,      // rotate the shape about its center
 *         kMorph_Style,       // transform each point, and turn lines into curves
 *
 *         kLastEnum_Style = kMorph_Style,
 *     };
 *
 *     /** Dash by replicating the specified path.
 *         @param path The path to replicate (dash)
 *         @param advance The space between instances of path
 *         @param phase distance (mod advance) along path for its initial position
 *         @param style how to transform path at each point (based on the current
 *                      position and tangent)
 *     */
 *     static sk_sp<SkPathEffect> Make(const SkPath& path, SkScalar advance, SkScalar phase, Style);
 *
 *     static void RegisterFlattenables();
 * }
 * ```
 */
public open class SkPath1DPathEffect {
  public enum class Style {
    kTranslate_Style,
    kRotate_Style,
    kMorph_Style,
    kLastEnum_Style,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkPath1DPathEffect::Make(const SkPath& path, SkScalar advance, SkScalar phase,
     *                                              Style style) {
     *     if (advance <= 0 || !SkIsFinite(advance, phase) || path.isEmpty()) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkPathEffect>(new SkPath1DPathEffectImpl(path, advance, phase, style));
     * }
     * ```
     */
    public fun make(
      path: SkPath,
      advance: SkScalar,
      phase: SkScalar,
      style: Style,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPath1DPathEffect::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkPath1DPathEffectImpl);
     * }
     * ```
     */
    public fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
