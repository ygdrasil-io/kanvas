package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.DashCubicsGM

/**
 * Cross-backend test : `DashCubicsGM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `SkDashPathEffect` on `drawPath`.
 * 865 x 750 canvas, 2 x 2 grid of cubic-Bezier "flower" SVG paths.
 * Each cell stacks 3 paints :
 *  - black fat stroke (width 42, default vs round join) ;
 *  - red half-width dashed stroke (width 21, dash `(5, 10)` or
 *    `(5.0002, 10)` to trigger the "shouldn't be integer" dasher
 *    edge case) ;
 *  - green hairline (width 0).
 *
 * Exercises dasher on cubic flattening, the round / miter join
 * combinations, and the integer-dash-interval quirk.
 *
 * Floors : GPU 92.64 % / raster 91.02 % (initial run GPU 92.69 % /
 * raster 91.07 %, ratchet 0.05 % below observed).
 */
class DashCubicsCrossBackendTest {

    @Test
    fun `DashCubicsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = DashCubicsGM(),
            rasterFloor = 91.02,
            gpuFloor = 92.64,
        )
    }
}
