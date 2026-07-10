package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathopsinverse.cpp` — `PathOpsInverseGM`.
 * Tests path boolean operations (Difference, Intersect, Union, XOR,
 * ReverseDifference) with even-odd and inverse-even-odd fill types.
 * @see https://github.com/google/skia/blob/main/gm/pathopsinverse.cpp
 */
class PathOpsInverseGm : SkiaGm {
    override val name = "pathopsinverse"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 900

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.PATHOPS_INVERSE — requires SkPathOps::Op with inverse fill types")
    }
}
