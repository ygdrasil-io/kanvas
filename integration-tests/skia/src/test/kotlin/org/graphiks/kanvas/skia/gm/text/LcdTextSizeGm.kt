package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/lcdtext.cpp::LcdTextSizeGM` (320 × 120).
 * Missing API: Font.edging (kSubpixelAntiAlias).
 * @see https://github.com/google/skia/blob/main/gm/lcdtext.cpp
 */
class LcdTextSizeGm : SkiaGm {
    override val name = "lcdtextsize"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 120
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires Font.edging for subpixel AA */
    }
}
