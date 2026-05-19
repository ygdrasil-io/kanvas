package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug887103GM

/**
 * Cross-backend test : `Crbug887103GM` on raster + GPU.
 *
 * 520 x 520 single `drawPath`. Three nearly-coincident triangles (each
 * `moveTo + lineTo + lineTo`) along the right edge, AA fill, default
 * `kWinding` rule. Reduced from a Chromium triangle-rasterisation
 * regression where edges at near-vertical orientations on the right
 * edge of the canvas lost coverage.
 *
 * Pure multi-contour AA polygon fill on stencil-and-cover (G3.3b.3a).
 *
 * Floors : raster 99.81 %, GPU 99.80 % (post-ratchet ~0.05 % below
 * measured).
 */
class Crbug887103CrossBackendTest {

    @Test
    fun `Crbug887103GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug887103GM(),
            rasterFloor = 99.81,
            gpuFloor = 99.80,
        )
    }
}
