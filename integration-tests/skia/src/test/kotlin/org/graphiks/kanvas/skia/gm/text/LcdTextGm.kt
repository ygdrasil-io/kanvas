package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/lcdtext.cpp::LcdTextGM` (640 × 480).
 * Missing API: Font.edging (kSubpixelAntiAlias).
 * @see https://github.com/google/skia/blob/main/gm/lcdtext.cpp
 */
class LcdTextGm : SkiaGm {
    override val name = "lcdtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires Font.edging for subpixel AA */
    }
}
