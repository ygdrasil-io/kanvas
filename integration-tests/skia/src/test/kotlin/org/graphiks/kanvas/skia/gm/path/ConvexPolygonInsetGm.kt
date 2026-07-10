package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/polygonoffset.cpp` — `PolygonOffsetGM(true)`.
 * Draws convex polygons with inset offsets using Skia-internal SkInsetConvexPolygon.
 * @see https://github.com/google/skia/blob/main/gm/polygonoffset.cpp
 */
class ConvexPolygonInsetGm : SkiaGm {
    override val name = "convex-polygon-inset"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.CONVEX_POLYGON_INSET — requires SkInsetConvexPolygon (Skia-internal utility)")
    }
}
