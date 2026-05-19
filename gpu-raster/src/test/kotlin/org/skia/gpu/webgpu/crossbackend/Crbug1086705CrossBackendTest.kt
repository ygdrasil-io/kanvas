package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug1086705GM

/**
 * Cross-backend test : `Crbug1086705GM` on raster + GPU.
 *
 * 200 x 200. 700-vertex polygon approximating a `r = 2` circle around
 * `(100, 100)`, stroked at width 5 (i.e. stroke wider than the source
 * shape -> heavy self-intersection). Originally exposed a convex-path
 * linearizing renderer regression that collapsed too many near-
 * duplicate vertices and degenerated the polygon into a triangle.
 *
 * Pure G3.4.1 stroker workout on a near-degenerate convex polyline
 * (700 lineTo verbs in a single contour), with self-intersecting
 * stroke geometry.
 *
 * Floors : raster 99.90 %, GPU 99.87 % (post-ratchet ~0.05 % below
 * measured).
 */
class Crbug1086705CrossBackendTest {

    @Test
    fun `Crbug1086705GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug1086705GM(),
            rasterFloor = 99.90,
            gpuFloor = 99.87,
        )
    }
}
