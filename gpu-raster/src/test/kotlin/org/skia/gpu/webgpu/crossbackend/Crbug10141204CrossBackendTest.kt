package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug10141204GM

/**
 * Cross-backend test : `Crbug10141204GM` on raster + GPU.
 *
 * 512 x 512. Extreme non-axis-aligned CTM stack
 * (`scale(exp(-2.3)) * scale(2) * concat(MakeAll(...)) *
 * translate(-3040103, 337502) * scale(9783.94, -9783.94)`) then
 * `drawRect(0, 0, 512, 512)` blue AA. The composed CTM should fill the
 * left half of the screen with solid blue. Originally exposed a
 * numerical issue in the GPU clip discard path under giant post-scale
 * coordinates (~10^6).
 *
 * Exercises drawRect dispatch under a non-affine-fast-path CTM with
 * extreme magnitudes — pure rect fill, no shader / filter.
 *
 * Floors : raster 99.95 %, GPU 99.95 % (post-ratchet ~0.05 % below
 * measured ; both backends byte-exact at 100.00 %).
 */
class Crbug10141204CrossBackendTest {

    @Test
    fun `Crbug10141204GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug10141204GM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
