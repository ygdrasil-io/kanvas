package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/textblobmixedsizes_df.cpp` (640 × 480).
 * Missing API: SDF text rendering.
 * @see https://github.com/google/skia/blob/main/gm/textblobmixedsizes_df.cpp
 */
class TextBlobMixedSizesDfGm : SkiaGm {
    override val name = "textblobmixedsizes_df"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires SDF text */
    }
}
