package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurQuickRejectGM

/**
 * Cross-backend test : `BlurQuickRejectGM` on raster + GPU.
 *
 * 300 x 300 canvas with a 100 x 100 clipRect and four blurred coloured
 * drawRect calls whose bboxes overlap the clip's corners and edges.
 * Each blurred rect (sigma ~17.8, radius 30) is paired with a 0-width
 * hairline outline. Exercises the #570 SkBlurMaskFilter(kNormal)
 * unlock with the clipRect + blurred-but-edge-touching draw pattern --
 * the regression repro for the "quick reject" classifier looking at
 * the original (unexpanded) draw bbox.
 *
 * Floors :
 *  - raster (tol=8) : 99.80 %
 *  - GPU (tol=8)    : 99.80 %
 * GPU - raster gap is 0.00 pt (matching pixels equal on both
 * backends) -- the mask-blur + clipRect path is bit-stable across the
 * raster / GPU split here.
 */
class BlurQuickRejectCrossBackendTest {

    @Test
    fun `BlurQuickRejectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BlurQuickRejectGM(),
            rasterFloor = 99.75,
            gpuFloor = 99.75,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
