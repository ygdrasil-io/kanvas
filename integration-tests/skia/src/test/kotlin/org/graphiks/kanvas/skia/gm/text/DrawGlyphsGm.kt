package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/drawglyphs.cpp` (640 × 480).
 * Missing API: drawGlyphs, drawGlyphsRSXform.
 * @see https://github.com/google/skia/blob/main/gm/drawglyphs.cpp
 */
class DrawGlyphsGm : SkiaGm {
    override val name = "drawglyphs"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires drawGlyphs / drawGlyphsRSXform */
    }
}
