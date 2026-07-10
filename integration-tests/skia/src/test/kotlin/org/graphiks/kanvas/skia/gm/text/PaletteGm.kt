package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/palette.cpp` (512 × 256).
 * Tests OpenType COLR/CPAL palette selection. Requires OpenTypeTypeface with font table manipulation.
 * @see https://github.com/google/skia/blob/main/gm/palette.cpp
 */
class PaletteGm : SkiaGm {
    override val name = "palette"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires OpenTypeTypeface font table manipulation */
    }
}
