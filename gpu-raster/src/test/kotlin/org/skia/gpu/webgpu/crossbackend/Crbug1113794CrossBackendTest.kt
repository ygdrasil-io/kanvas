package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug1113794GM

/**
 * Cross-backend test : `Crbug1113794GM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `SkDashPathEffect` on `drawPath`.
 * 600 x 200 canvas. Vertical line `(50, 80) -> (50, 20)` stroked
 * 0.25 px AA, dashed `[10, 10]` phase 0, drawn under a viewBox
 * `RectToRect` matrix `100x100 -> 600x200`. Chromium regression cover
 * for the dasher mis-handling sub-pixel-wide strokes under non-
 * uniform scale.
 *
 * Floors : GPU 99.95 % / raster 99.85 % (initial run GPU 100.00 %
 * byte-exact, raster 99.90 % ; ratchet 0.05 % below observed).
 */
class Crbug1113794CrossBackendTest {

    @Test
    fun `Crbug1113794GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug1113794GM(),
            rasterFloor = 99.85,
            gpuFloor = 99.95,
        )
    }
}
