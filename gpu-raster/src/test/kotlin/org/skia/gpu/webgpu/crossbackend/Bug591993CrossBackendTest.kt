package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug591993GM

/**
 * Cross-backend test : `Bug591993GM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `SkDashPathEffect` on `drawPath`.
 * 40 x 140 canvas. Single dashed line (20, 20) -> (120, 20) stroked
 * 10 px wide with round caps and dash `[100, 100]` phase 100 -- the
 * dasher should produce a single fully-painted stroke segment topped
 * by round caps. Verifies dasher-cap interaction when phase lands the
 * start exactly on an "off" interval.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run both byte-exact
 * 100.00 % ; ratchet 0.05 % below observed).
 */
class Bug591993CrossBackendTest {

    @Test
    fun `Bug591993GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug591993GM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
