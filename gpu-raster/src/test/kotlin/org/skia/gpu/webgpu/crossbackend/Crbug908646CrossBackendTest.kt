package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug908646GM

/**
 * Cross-backend test : `Crbug908646GM` on raster + GPU.
 *
 * 300 x 300 single `drawPath`. An outer 4-vertex line-only square plus
 * two interior triangles, filled with `kEvenOdd`. The even-odd rule
 * should leave the triangles as holes inside the square. Reduced from
 * a Chromium fill-rule regression where the rasteriser didn't subtract
 * inner contours wound the same direction.
 *
 * Pure multi-contour AA polygon fill on stencil-and-cover (G3.3b.3a)
 * with `kEvenOdd`.
 *
 * Floors : raster 99.73 %, GPU 99.95 % (post-ratchet ~0.05 % below
 * measured ; GPU is 0.22 pt over raster).
 */
class Crbug908646CrossBackendTest {

    @Test
    fun `Crbug908646GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug908646GM(),
            rasterFloor = 99.73,
            gpuFloor = 99.95,
        )
    }
}
