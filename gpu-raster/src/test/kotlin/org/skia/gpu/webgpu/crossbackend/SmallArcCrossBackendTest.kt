package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SmallArcGM

/**
 * Cross-backend test : `SmallArcGM` on raster + GPU.
 *
 * Single AA cubic stroked at width 120 under `translate(-400,-400) ;
 * scale(8, 8)`. Stresses the resScale-aware stroker on a curve at a
 * moderate CTM scale. G3.4.1 stroke coverage routed recursively as an
 * AA single-contour concave fill.
 *
 * Floors : GPU 99.75 % / raster 99.76 % (initial run 99.80 % / 99.81 %).
 */
class SmallArcCrossBackendTest {

    @Test
    fun `SmallArcGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = SmallArcGM(),
            rasterFloor = 99.76,
            gpuFloor = 99.75,
        )
    }
}
