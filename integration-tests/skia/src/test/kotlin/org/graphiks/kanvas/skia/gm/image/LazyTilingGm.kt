package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/lazytiling.cpp::LazyTilingGM` (256x256).
 *
 * STUB: Requires GrDirectContext and lazy proxy creation, GPU-only.
 * @see https://github.com/google/skia/blob/main/gm/lazytiling.cpp
 */
class LazyTilingGm : SkiaGm {
    override val name = "lazytiling"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: GPU lazy proxy tiling not available in Kanvas
    }
}
