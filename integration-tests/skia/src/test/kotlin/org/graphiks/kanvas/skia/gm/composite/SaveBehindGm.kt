package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(save_behind, ...)` (830 × 670).
 * Tests SkCanvasPriv::SaveBehind / DrawBehind — private Skia API.
 * Missing API: saveBehind/drawBehind on GmCanvas.
 * @see https://github.com/google/skia/blob/main/gm/savelayer.cpp
 */
class SaveBehindGm : SkiaGm {
    override val name = "save_behind"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 830
    override val height = 670
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires saveBehind/drawBehind API */
    }
}
