package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokeRectsGM

/**
 * Cross-backend test : `StrokeRectsGM` on raster + GPU.
 *
 * Grid of stroked rects with varying stroke widths + join styles
 * (miter / round / bevel). Stresses the rect-stroker outline emitter ;
 * both backends should agree on the rectified corner geometry.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`StrokeRectsTest`, tol=1) : 80.0 %
 *  - GPU (`StrokeRectsWebGpuTest`, tol=8) : 99.81 %
 */
class StrokeRectsCrossBackendTest {

    @Test
    fun `StrokeRectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokeRectsGM(),
            rasterFloor = 80.0,
            gpuFloor = 99.81,
            rasterTolerance = 1,
        )
    }
}
