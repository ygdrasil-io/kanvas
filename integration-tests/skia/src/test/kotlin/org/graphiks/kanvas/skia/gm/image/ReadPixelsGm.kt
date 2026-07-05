package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/readpixels.cpp::ReadPixelsGM` (384x576).
 *
 * STUB: Requires `SkImage.readPixels` round-trip across color type / alpha type / color space
 * combinations driven by a raster source `SkImage`. The source fixture `images/google_chrome.ico`
 * is not shipped and the ICO decoder is stubbed (STUB.ICO_DECODE).
 * @see https://github.com/google/skia/blob/main/gm/readpixels.cpp
 */
class ReadPixelsGm : SkiaGm {
    override val name = "readpixels"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 576

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: missing fixture google_chrome.ico and ICO decoder
    }
}
