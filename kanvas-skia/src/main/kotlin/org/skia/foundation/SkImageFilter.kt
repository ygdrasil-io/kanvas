package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkImageFilter`](https://github.com/google/skia/blob/main/include/core/SkImageFilter.h)
 * — a per-draw transformation that takes the rasterised pixels (or
 * a `drawImage` source) into a new image, before the
 * [SkPaint.colorFilter] / [SkPaint.blendMode] stages.
 *
 * **Phase 7a status** : abstract base only — the slot exists on
 * [SkPaint] so client GMs can be ported without churn, but no
 * concrete subclass ships yet (planned : Phase 7d with the offset /
 * blur / drop-shadow / matrix-transform / compose family). Setting
 * `paint.imageFilter = nonNull` is currently a silent no-op in the
 * rasterizer ; tests should not rely on visual changes.
 *
 * The integration point is in [org.skia.core.SkBitmapDevice]'s
 * `drawImageRect` and `saveLayer` paths. The hook will be plumbed
 * alongside the first concrete subclass.
 */
public abstract class SkImageFilter {
    // Intentionally empty. See the doc above for Phase 7d roadmap.
}
