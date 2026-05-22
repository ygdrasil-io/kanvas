package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BitmapRectTestGM

/**
 * Cross-backend test : `BitmapRectTestGM` on raster + GPU.
 *
 * 320 x 240 ; source bitmap is a CPU `SkCanvas(SkBitmap)` (filled
 * triangle + stroked outline, 60 x 60) drawn three times :
 *  1. `drawImage` at (150, 45) -- 1:1 placement,
 *  2. under `scale(0.472560018)` : `drawImageRect` into (100, 100,
 *     228, 228),
 *  3. under `scale(-1, 1)` : `drawImage` at (-310, 45) -- axis-aligned
 *     horizontal flip.
 *
 * Regression repro for upstream `gm/bitmaprecttest.cpp` where the
 * `drawImageRect` fast path under scale duplicated the right column of
 * source pixels. Verifies the slow-path fix on both backends + the
 * axis-aligned reflection (G5.1 / G5.1.1) parity.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round10Test`, tol=1) : 80.0 % (intentionally lax) ;
 *  - GPU (`BitmapRectTestWebGpuTest`, tol=8) : 98.45 %.
 */
class BitmapRectTestCrossBackendTest {

    @Test
    fun `BitmapRectTestGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BitmapRectTestGM(),
            rasterFloor = 80.0,
            gpuFloor = 98.45,
            rasterTolerance = 1,
        )
    }
}
