package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/ycbcrimage.cpp::YCbCrImageGM` (128x128).
 *
 * STUB: Vulkan-only GM that exercises native YCbCr sampler (VK_YCbCrSampler).
 * @see https://github.com/google/skia/blob/main/gm/ycbcrimage.cpp
 */
class YCbCrImageGm : SkiaGm {
    override val name = "ycbcrimage"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: Vulkan YCbCr sampler not available in Kanvas
    }
}
