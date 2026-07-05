package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/lcdtext.cpp::SaveLayerPreserveLCDTextGM` (620 × 300).
 * Missing API: SaveLayerFlags (kPreserveLCDText).
 * @see https://github.com/google/skia/blob/main/gm/lcdtext.cpp
 */
class SaveLayerPreserveLcdTextGm : SkiaGm {
    override val name = "savelayerpreservelcdtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 620
    override val height = 300
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires SaveLayerFlags for LCD text preservation */
    }
}
