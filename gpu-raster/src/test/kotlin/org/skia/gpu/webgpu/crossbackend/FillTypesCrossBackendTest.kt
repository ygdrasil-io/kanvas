package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.FillTypesGM

/**
 * Cross-backend test : `FillTypesGM` on raster + GPU.
 *
 * 4-by-2 grid (winding / even-odd x inverse-on/off) of overlapping
 * circle paths stroked + filled, exercising the four `SkPathFillType`
 * enumerants on a single concave path. Pure G3.3b polygon AA on
 * non-convex paths.
 *
 * Floors : GPU 99.50 % / raster 99.58 % (initial run 99.55 % / 99.63 %).
 */
class FillTypesCrossBackendTest {

    @Test
    fun `FillTypesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = FillTypesGM(),
            rasterFloor = 99.58,
            gpuFloor = 99.50,
        )
    }
}
