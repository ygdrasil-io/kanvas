package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.DashingGM

/**
 * Cross-backend test : `DashingGM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `paint.pathEffect` dispatch on
 * `drawPath` routes `SkDashPathEffect` through `filterPath` before
 * the stroker. This GM is the canonical dash workout : 640 x 340
 * canvas, 12-row main grid of dashed `drawLine` calls combining
 * 3 stroke widths {0, 1, 8} x 2 patterns {1:1, 4:1} x 2 AA modes,
 * plus the giant-dash regression line (20 000-unit length, 1:1
 * pattern, exercises near-zero delta-T), zero-length degenerate
 * dashes (no draw expected), and the empty 0:0 dash row.
 *
 * Floors : GPU 96.75 % / raster 96.03 % (initial run GPU 96.80 % /
 * raster 96.08 %, ratchet 0.05 % below observed).
 */
class DashingCrossBackendTest {

    @Test
    fun `DashingGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = DashingGM(),
            rasterFloor = 96.03,
            gpuFloor = 96.75,
        )
    }
}
