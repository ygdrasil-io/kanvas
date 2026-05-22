package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/drawimageset.cpp::DrawImageSetGM` (1000 × 725).
 *
 * Exercises `SkCanvas::experimental_DrawImageSet()` — the batched
 * `SkCanvas::ImageSetEntry[]` API. Each entry is an image + src
 * rect + dst rect + per-entry alpha + AA flag bits ; the bulk call
 * lets a renderer fuse N image draws into one operation (key for
 * the tile-based path in `SkRasterPipeline*` / GPU command buffers).
 *
 * **API gap** : [org.skia.core.SkCanvas] has no `drawImageSet` /
 * `ImageSetEntry` yet. The shape is non-trivial (per-entry AA flags
 * are quad-edge bitmasks ; CTM interaction with the perspective
 * fallback is subtle). The single-quad case is covered by
 * [org.skia.core.SkCanvas.drawImageRect] today ; the batched
 * fast-path is the missing piece. Stub keeps the class registered.
 */
public class DrawImageSetGM : GM() {
    init { setBGColor(0xFFCCCCCC.toInt()) }
    override fun getName(): String = "draw_image_set"
    override fun getISize(): SkISize = SkISize.Make(1000, 725)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once SkCanvas.drawImageSet (or experimental
        //   variant) is exposed. Upstream impl loops over a
        //   `ImageSetEntry[]` and dispatches one drawImageRect each
        //   on the slow path, or a fused tile-rect on the fast path.
    }
}
