package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConjoinedPolygonsGM

/**
 * Cross-backend test : `ConjoinedPolygonsGM` (`conjoined_polygons`) on
 * raster + GPU.
 *
 * 400 x 400, single self-touching 7-vertex bow-tie path drawn AA-filled.
 * Regression repro for `crbug.com/1197461` -- the triangulator's handling
 * of a polygon that touches itself at a single vertex. Pure axis-aligned
 * `drawPath` workout (no rotate / skew / shader). G3.3b polygon AA on a
 * non-convex, self-touching path.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round4Test`, tol=1) : 98.0 % ;
 *  - GPU (`ConjoinedPolygonsWebGpuTest`, tol=8) : 99.80 %.
 */
class ConjoinedPolygonsCrossBackendTest {

    @Test
    fun `ConjoinedPolygonsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ConjoinedPolygonsGM(),
            rasterFloor = 98.0,
            gpuFloor = 99.80,
            rasterTolerance = 1,
        )
    }
}
