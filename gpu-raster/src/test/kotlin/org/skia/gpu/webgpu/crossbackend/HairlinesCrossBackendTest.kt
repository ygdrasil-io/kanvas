package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.HairlinesGM

/**
 * Cross-backend test : `HairlinesGM` on raster + GPU.
 *
 * 1250 x 1250 canvas, 14 stress paths x 3 stroke widths
 * `{0, 0.5, 1.5}` x 2 AA modes x 2 alpha values `{0xFF, 0x40}` = 168
 * draws total. Paths include : 15-spoke star, near-vertical-tangent
 * quads (skbug regressions, degenerate tangent at t=1), cubic-cusp
 * regression paths, missing-end-cap line bundles, and a `r=2000`
 * arc + chord. Stroker degenerate-vertex exposure on a large grid
 * (#567 unlock).
 *
 * Floors :
 *  - raster (tol=1) : 97.68 %
 *  - GPU (tol=8) : 98.97 %
 */
class HairlinesCrossBackendTest {

    @Test
    fun `HairlinesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = HairlinesGM(),
            rasterFloor = 97.63,
            gpuFloor = 98.92,
            rasterTolerance = 1,
        )
    }
}
