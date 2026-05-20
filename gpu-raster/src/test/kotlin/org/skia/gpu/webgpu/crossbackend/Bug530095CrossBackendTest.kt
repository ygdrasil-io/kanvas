package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug530095GM

/**
 * Cross-backend test : `Bug530095GM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `SkDashPathEffect` on `drawPath`.
 * This GM is the dasher extreme-intervals regression cover :
 *  - circle r=124 stroke 26, dash `[700, 700]` phase -40 (giant
 *    interval ~= circle perimeter) at translate(0, 0) + (0, 400) ;
 *  - the same shape at 1/100 scale (r=1.24, stroke 0.26, dash
 *    `[7, 7]`) under `scale(100, 100)` -- validates dasher under
 *    CTM zoom.
 *
 * Floors : GPU 97.35 % / raster 97.10 % (initial run GPU 97.41 % /
 * raster 97.16 %, ratchet 0.05 % below observed).
 */
class Bug530095CrossBackendTest {

    @Test
    fun `Bug530095GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug530095GM(),
            rasterFloor = 97.10,
            gpuFloor = 97.35,
        )
    }
}
