package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/imagefilters.cpp::SaveLayerWithBackdropGM` (830x550).
 *
 * STUB: Requires `saveLayer(SaveLayerRec)` with backdrop `SkImageFilter` across four filter
 * types (blur, dilate, matrix convolution, color filter) and image resource loading.
 * GmCanvas lacks backdrop filter support in `saveLayer`.
 * @see https://github.com/google/skia/blob/main/gm/imagefilters.cpp
 */
class SaveLayerWithBackdropGm : SkiaGm {
    override val name = "savelayer_with_backdrop"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 830
    override val height = 550

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop SaveLayerRec not available
    }
}
