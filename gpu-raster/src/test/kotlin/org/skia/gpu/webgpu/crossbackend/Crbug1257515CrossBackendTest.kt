package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug1257515GM

/**
 * Cross-backend test : `Crbug1257515GM` on raster + GPU.
 *
 * 1139 x 400, 2 long polylines stroked under `translate + scale(2,2)`.
 * Red polyline : 12 vertices, sw=2, kRound_Cap + kRound_Join. Blue
 * polyline : 10 vertices, sw=3, kButt_Cap + kBevel_Join, strokeMiter=10.
 * Regression for iOS/Chromium SVG polyline stroke under non-1 scale.
 * Exercises G3.4.1 SkStroker on long polylines + round/bevel joins.
 */
class Crbug1257515CrossBackendTest {

    @Test
    fun `Crbug1257515GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug1257515GM(),
            rasterFloor = 99.21,
            gpuFloor = 99.55,
        )
    }
}
