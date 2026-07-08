package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/image.cpp::new_texture_image` (280x115).
 *
 * STUB: GPU-only GM that creates images from raster, encoded (PNG/JPEG), picture,
 * and texture sources. Requires GPU context.
 * @see https://github.com/google/skia/blob/main/gm/image.cpp
 */
class NewTextureImageGm : SkiaGm {
    override val name = "new_texture_image"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 280
    override val height = 115

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: GPU-only texture image creation test
    }
}
