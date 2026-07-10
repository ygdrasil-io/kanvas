package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/rsx_blob_shader.cpp` (881 × 881).
 * Missing API: RSX transforms on text blobs.
 * @see https://github.com/google/skia/blob/main/gm/rsx_blob_shader.cpp
 */
class RsxBlobShaderGm : SkiaGm {
    override val name = "rsx_blob_shader"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 881
    override val height = 881
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires RSX transform support */
    }
}
