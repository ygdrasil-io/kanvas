package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClipDrawDrawGM

/**
 * Cross-backend test : `ClipDrawDrawGM` on raster + GPU.
 *
 * Repro for crbug.com/423834 : `clipRect + drawRect + drawRect`
 * sequences that historically left 1-px remnants when integer-edge
 * rounding diverged between `clipRect` and `drawRect`. Pure
 * axis-aligned, non-AA fill rects with `clipRect` on the rect
 * fast-path. No stroke, no path, no shader.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %,
 * byte-exact on both backends thanks to round-half-up edge rounding shared
 * with `SkBitmapDevice`).
 */
class ClipDrawDrawCrossBackendTest {

    @Test
    fun `ClipDrawDrawGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClipDrawDrawGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
