package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkMaskFilter`](https://github.com/google/skia/blob/main/include/core/SkMaskFilter.h)
 * — a transformation of the rasteriser's coverage mask before the
 * paint colour is composited.
 *
 * **Phase 7a status** : abstract base only — the slot exists on
 * [SkPaint] so client GMs can be ported without churn, but no
 * concrete subclass ships yet (planned : Phase 7c with Gaussian
 * blur). Setting `paint.maskFilter = nonNull` is currently a silent
 * no-op in the rasterizer ; tests should not rely on visual changes.
 *
 * The integration point is in [org.skia.core.SkBitmapDevice]'s path /
 * rect rasterisers : after the coverage mask is computed but before
 * the colour composite, the mask filter would re-process the alpha
 * channel (e.g. Gaussian-blur it, then re-rasterize the dilated
 * shape). The hook will be plumbed alongside the first concrete
 * subclass.
 */
public abstract class SkMaskFilter {
    // Intentionally empty. See the doc above for Phase 7c roadmap.
}
