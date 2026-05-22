package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.LatticeGM

/**
 * Cross-backend test : `LatticeGM` on raster + GPU.
 * drawImageLattice with two configurations (4x4 default, 5x5 with
 * kFixedColor + kTransparent overrides) over a 220x220 stripe-rect
 * image.
 */
class LatticeCrossBackendTest {

    @Test
    fun `LatticeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = LatticeGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
