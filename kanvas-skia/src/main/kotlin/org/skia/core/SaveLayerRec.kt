package org.skia.core

import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

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
)
