package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathopsblend.cpp` — `DEF_SIMPLE_GM(pathops_blend, ...)`.
 * Exercises PathOps (Union, Intersect, Difference, XOR) approximated via
 * blend-mode layers, comparing the PathOps result to a raster-mask approach.
 * @see https://github.com/google/skia/blob/main/gm/pathopsblend.cpp
 */
class PathOpsBlendGm : SkiaGm {
    override val name = "pathops_blend"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 130
    override val height = 310

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.PATHOPS_BLEND — requires SkPathOps::Op or equivalent blend-mode compositing")
    }
}
