package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/drawimageset.cpp::DrawImageSetRectToRectGM`
 * (1305 × 540).
 *
 * The src→dst rect mapping variant of the batched `drawImageSet`
 * API : each `ImageSetEntry` carries its own per-entry `src`
 * (sub-rect of the image) and `dst` (target on the canvas). Tests
 * the tile-rect fast path's subset handling.
 *
 * **API gap** : same `drawImageSet` shape as [DrawImageSetGM]. Stub
 * keeps the class registered.
 */
public class DrawImageSetRectToRectGM : GM() {
    override fun getName(): String = "draw_image_set_rect_to_rect"
    override fun getISize(): SkISize = SkISize.Make(1305, 540)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once SkCanvas.drawImageSet exists ; same
        //   blocker as DrawImageSetGM.
    }
}
