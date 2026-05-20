package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug888453GM

/**
 * Cross-backend test : `Crbug888453GM` on raster + GPU.
 *
 * 480 x 150, 19 small full-circle arcs at increasing radii drawn in 3
 * rows : AA-filled, AA-hairline, AA-stroked (width 2). Originally a
 * regression test for too-large conic->quad chopping tolerance on small
 * circles. Pure G3.4 drawArc + G3.4.1 SkStroker, no shader / mask
 * filter.
 */
class Crbug888453CrossBackendTest {

    @Test
    fun `Crbug888453GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug888453GM(),
            rasterFloor = 92.81,
            gpuFloor = 92.81,
        )
    }
}
