package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/matrixconvolution.cpp` (500 × 300, 6 variants).
 * Tests SkImageFilters::MatrixConvolution with various kernel sizes.
 * Missing API: MatrixConvolution image filter.
 * @see https://github.com/google/skia/blob/main/gm/matrixconvolution.cpp
 */
open class MatrixConvolutionGm(
    private val nameSuffix: String,
) : SkiaGm {
    override val name = "matrixconvolution$nameSuffix"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires MatrixConvolution image filter */
    }
}

class MatrixConvolutionColorGm : MatrixConvolutionGm("_color")
class MatrixConvolutionBigGm : MatrixConvolutionGm("_big")
class MatrixConvolutionBigColorGm : MatrixConvolutionGm("_big_color")
class MatrixConvolutionBiggerGm : MatrixConvolutionGm("_bigger")
class MatrixConvolutionBiggestGm : MatrixConvolutionGm("_biggest")
