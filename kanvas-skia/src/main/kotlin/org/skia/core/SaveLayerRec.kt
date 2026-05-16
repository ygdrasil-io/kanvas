package org.skia.core

import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect

/**
 * Mirrors Skia's
 * [`SkCanvas::SaveLayerRec`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h#L1280)
 * — the bundle of optional parameters consumed by the full-fat
 * `saveLayer(SaveLayerRec)` overload.
 *
 * Fields :
 *  - [bounds] — the layer's local-space bbox. `null` ⇒ "as big as the
 *    current clip". Mirrors `fBounds` in upstream.
 *  - [paint] — alpha / colour-filter / image-filter / blend-mode used
 *    when compositing the layer back onto its parent in [SkCanvas.restore].
 *    Mirrors `fPaint`.
 *  - [backdrop] — an [SkImageFilter] run against the parent device's
 *    pixels (within [bounds]) **before** any draws into the layer.
 *    The filtered backdrop becomes the layer's initial content.
 *    Mirrors `fBackdrop`.
 *  - [flags] — the upstream `SaveLayerFlags` bitfield. Currently a
 *    no-op (every flag bit gates a feature that the raster path
 *    doesn't implement yet) ; accepted for source-compat.
 *
 * **Phase G6** introduces this type alongside
 * [SkCanvas.saveLayer] `(rec)`. The existing two-arg
 * [SkCanvas.saveLayer] `(bounds, paint)` overload is preserved and
 * routes through `SaveLayerRec(bounds, paint, null, 0)` for code
 * sharing.
 */
public data class SaveLayerRec(
    public val bounds: SkRect? = null,
    public val paint: SkPaint? = null,
    public val backdrop: SkImageFilter? = null,
    public val flags: SaveLayerFlags = 0,
    /**
     * Phase R1-C — mirrors Skia's private
     * `SaveLayerRec::fExperimentalBackdropScale`
     * (`include/core/SkCanvas.h::SaveLayerRec` — see `internalDrawDeviceWithFilter`'s
     * `scaleFactor` parameter, `include/core/SkCanvas.h:2654-2660`).
     *
     * Uniform downscale applied to the layer's **backdrop snapshot**
     * before it's run through [backdrop] and pasted into the new layer.
     * Larger values (`scaleFactor < 1`) downsample the snapshot, which
     * speeds up the filter and produces a slightly softer / blockier
     * starting point ; `1.0` (the default) takes the snapshot at full
     * resolution.
     *
     * Used by upstream's `backdrop_scalefactor.cpp` GM. Default
     * `1.0f` matches the existing two-arg `saveLayer(bounds, paint)`
     * overload's previous behaviour.
     */
    public val scaleFactor: Float = 1f,
)
