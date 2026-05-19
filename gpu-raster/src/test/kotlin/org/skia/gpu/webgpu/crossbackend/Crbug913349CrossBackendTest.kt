package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug913349GM

/**
 * Cross-backend test : `Crbug913349GM` on raster + GPU.
 *
 * 500 x 600 single `drawPath`. 5-vertex line-only contour with a
 * 2-pixel-tall near-degenerate sliver at the bottom (`y = 224 .. 226`),
 * filled with AA + default `kWinding`. Reduced from a Chromium polygon-
 * fill bug where the AA edge convention on the sliver dropped
 * coverage.
 *
 * Pure AA polygon fill on stencil-and-cover (G3.3b.3a).
 *
 * Floors : raster 99.77 %, GPU 99.86 % (post-ratchet ~0.05 % below
 * measured).
 */
class Crbug913349CrossBackendTest {

    @Test
    fun `Crbug913349GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug913349GM(),
            rasterFloor = 99.77,
            gpuFloor = 99.86,
        )
    }
}
