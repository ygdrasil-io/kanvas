package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/attributes.cpp::AttributesGM`.
 *
 * Ganesh-internal GM exercising vertex-attribute offsets and strides.
 * Not portable to raster — no-op stub.
 * @see https://github.com/google/skia/blob/main/gm/attributes.cpp
 */
class AttributesGm : SkiaGm {
    override val name = "attributes"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 120
    override val height = 340

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
