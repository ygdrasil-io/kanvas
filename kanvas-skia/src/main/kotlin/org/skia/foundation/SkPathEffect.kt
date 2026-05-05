package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkPathEffect`](https://github.com/google/skia/blob/main/include/core/SkPathEffect.h)
 * — a path → path transformation applied before the stroker /
 * rasteriser. Examples : dashing (decompose path into stipples),
 * corner rounding, point spreading, 1D / 2D path tiling.
 *
 * **Phase 7a status** : abstract base only — the slot exists on
 * [SkPaint] so client GMs can be ported without churn, but no
 * concrete subclass ships yet (planned : Phase 7b with the dash /
 * corner / discrete family). Setting `paint.pathEffect = nonNull`
 * is currently a silent no-op in the rasterizer ; tests should not
 * rely on visual changes.
 *
 * The integration point is in [org.skia.core.SkBitmapDevice.drawPath]
 * and the path-fill flavour of `drawRect`. The hook will be plumbed
 * alongside the first concrete subclass.
 */
public abstract class SkPathEffect {
    // Intentionally empty. See the doc above for Phase 7b roadmap.
}
