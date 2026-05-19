package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClippedCubicGM

/**
 * Cross-backend test : `ClippedCubicGM` on raster + GPU.
 *
 * 3 x 3 grid of cells, each clipping a self-intersecting cubic at
 * `(0,0)` -> `(170, 150)` to the path's own bounding box, then
 * translating by `(dx, dy)` in {-1, 0, +1} px before drawing. The
 * dx/dy shifts pull a sliver of the cubic outside the clip in each
 * cell, exposing the scanline rasterizer's clip-edge arithmetic on
 * a curve. Default black fill, default kWinding fill rule.
 *
 * Pure G3.3b.1 cubic flattening + G3.3b.3a AA stencil-and-cover fill
 * + clipRect interaction on both backends.
 *
 * Floors :
 *  - raster (tol=1) : 99.92 %
 *  - GPU (tol=8) : 99.94 %
 */
class ClippedCubicCrossBackendTest {

    @Test
    fun `ClippedCubicGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClippedCubicGM(),
            rasterFloor = 99.92,
            gpuFloor = 99.94,
            rasterTolerance = 1,
        )
    }
}
