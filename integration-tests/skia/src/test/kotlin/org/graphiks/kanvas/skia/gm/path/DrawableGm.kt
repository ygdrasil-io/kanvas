package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/drawable.cpp` (180x275).
 *
 * STUB: Requires `SkDrawable.drawDrawable` — the SkDrawable extension slot is not available
 * on GmCanvas. The GM exercises drawable rendering under four CTM variants.
 * @see https://github.com/google/skia/blob/main/gm/drawable.cpp
 */
class DrawableGm : SkiaGm {
    override val name = "drawable"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 180
    override val height = 275

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: SkDrawable.drawDrawable not supported on GmCanvas
    }
}
