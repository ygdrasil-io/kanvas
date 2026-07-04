package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/fancyblobunderline.cpp` (640 × 480).
 * Missing API: text decoration / underline on blobs.
 * @see https://github.com/google/skia/blob/main/gm/fancyblobunderline.cpp
 */
class FancyBlobUnderlineGm : SkiaGm {
    override val name = "fancyblobunderline"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires text decoration support */
    }
}
