package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/drawimageset.cpp::DrawImageSetAlphaOnlyGM`
 * (440 × 250).
 *
 * Mirrors [DrawImageSetGM] but using an `Alpha-8` / `R8` source
 * image — exercises the alpha-only image path through the batched
 * `drawImageSet` API (the per-entry alpha multiplier is the
 * driving probe).
 *
 * **API gap** : same `drawImageSet` shape as [DrawImageSetGM]. Stub
 * keeps the class registered.
 */
public class DrawImageSetAlphaOnlyGM : GM() {
    override fun getName(): String = "draw_image_set_alpha_only"
    override fun getISize(): SkISize = SkISize.Make(440, 250)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once SkCanvas.drawImageSet exists ; same
        //   blocker as DrawImageSetGM.
    }
}
