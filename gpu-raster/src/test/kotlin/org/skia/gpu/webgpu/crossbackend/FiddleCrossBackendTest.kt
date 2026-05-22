package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.FiddleGM

/**
 * Cross-backend test : `FiddleGM` on raster + GPU.
 *
 * Tiny "fiddle" -- 2 filled rects of solid colour exercising the
 * drawRect fast path with axis-aligned non-AA fill. Both backends
 * byte-exact against the reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class FiddleCrossBackendTest {

    @Test
    fun `FiddleGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = FiddleGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
