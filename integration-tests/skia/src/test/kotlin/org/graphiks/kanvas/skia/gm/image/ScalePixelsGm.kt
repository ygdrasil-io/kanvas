package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/scalepixels.cpp::ScalePixelsGM` (960x720).
 *
 * STUB: Requires `SkPixmap::scalePixels` pixmap-direct resampling API — only the canvas-based
 * draw path is exposed in Kanvas. The GM verifies independent pixmap resampling with various
 * sampling options, not the draw-based path.
 * @see https://github.com/google/skia/blob/main/gm/scalepixels.cpp
 */
class ScalePixelsGm : SkiaGm {
    override val name = "scale-pixels"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 960
    override val height = 720

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: SkPixmap.scalePixels not exposed in Kanvas
    }
}
