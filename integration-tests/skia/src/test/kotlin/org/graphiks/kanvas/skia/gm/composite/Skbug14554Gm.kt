package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(skbug_14554, ...)` (310 × 630).
 * Tests saveLayer alpha collapse bug with drawAtlas, drawVertices, drawPoints, drawImageSet.
 * Missing API: PictureRecorder, drawVertices, drawAtlas on GmCanvas.
 * @see https://github.com/google/skia/blob/main/gm/savelayer.cpp
 */
class Skbug14554Gm : SkiaGm {
    override val name = "skbug_14554"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 310
    override val height = 630
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires PictureRecorder, drawVertices, drawAtlas */
    }
}
