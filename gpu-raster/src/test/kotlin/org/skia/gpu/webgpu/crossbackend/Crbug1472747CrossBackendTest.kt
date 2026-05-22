package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug1472747GM

/**
 * Cross-backend test : `Crbug1472747GM` (`crbug_1472747`) on raster + GPU.
 *
 * 400 x 400 single `drawPath` : inner + outer ovals at r=31000 / r=31005
 * filled with `kEvenOdd` to produce a thin ring. Each oval is built from
 * two 180-degree `arcTo` half-arcs as Canvas2D would. Tests the
 * even-odd fill rule on multi-contour input with conic-flattened arcs at
 * extreme radius (G3.3b.3b on top of conic flattening from G3.3b.1).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round6Test`, tol=1) : 96.0 % ;
 *  - GPU (`Crbug1472747WebGpuTest`, tol=8) : 98.10 %.
 */
class Crbug1472747CrossBackendTest {

    @Test
    fun `Crbug1472747GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug1472747GM(),
            rasterFloor = 96.0,
            gpuFloor = 98.10,
            rasterTolerance = 1,
        )
    }
}
