package org.skia.foundation

import org.skia.core.SkCanvas
import org.skia.math.SkPoint

/**
 * Mirrors Skia's
 * [`sktext::gpu::Slug`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)
 * (forward-declared as `SkTextSlug` in the
 * `SkCanvas::drawSlug` API surface) — a pre-compiled,
 * **replay-able** snapshot of a sub-run of glyphs that a backend
 * may cache and re-draw cheaply.
 *
 * **R-suivi.50 minimal port** : we model a slug as a captured
 * [SkTextBlob] + [SkPaint] pair. [replay] re-issues the captured
 * draw against an arbitrary [SkCanvas] at an arbitrary [origin]
 * — matching upstream's contract that a slug can be played back
 * into any canvas of the same coord-space class.
 *
 * The upstream GPU-backed compilation pipeline (atlas residency,
 * pre-tessellated paths, …) is out of scope ; the goal here is
 * to surface the API entry point on [SkCanvas] and let downstream
 * subclasses (e.g. `SkRecordingCanvas`, `SkNWayCanvas`,
 * `SkNoDrawCanvas`) override / forward / drop slug draws cleanly.
 *
 * @param blob captured glyph run sequence to replay.
 * @param paint paint to apply to every replay (colour, blend mode,
 *  shader, mask filter, …).
 * @param origin baked-in origin of the slug ; subsequent
 *  [replay] calls translate from this origin to the replay
 *  [SkPoint], so the slug captures **relative** glyph positions.
 */
public class SkTextSlug(
    public val blob: SkTextBlob,
    public val paint: SkPaint,
    public val origin: SkPoint = SkPoint(0f, 0f),
) {
    /**
     * Replay the captured glyph runs against [canvas] anchored at
     * [target]. Mirrors upstream `SkSlug::draw(canvas)` (which
     * implicitly anchors at the slug's recorded origin) ; we make
     * the anchor explicit so callers can re-position a cached slug
     * without rebuilding it.
     */
    public fun replay(canvas: SkCanvas, target: SkPoint = origin) {
        val dx = target.fX - origin.fX
        val dy = target.fY - origin.fY
        canvas.drawTextBlob(blob, dx, dy, paint)
    }
}
