package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BeziersGM

/**
 * Cross-backend test : `BeziersGM` on raster + GPU.
 *
 * Two panes of 10 random AA-stroked Bezier paths each — top uses
 * `moveTo + 2 × quadTo`, bottom uses `moveTo + 2 × cubicTo`. Stroke
 * widths span ~1-25 px. Exercises the quad + cubic flatten flows on
 * both backends ; SkRandom is bit-compatible with upstream, so the
 * geometry + colour stream matches.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`BeziersTest`, tol=1) : 88.0 %
 *  - GPU (`BeziersWebGpuTest`, tol=8) : 96.9 %
 */
class BeziersCrossBackendTest {

    @Test
    fun `BeziersGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BeziersGM(),
            rasterFloor = 88.0,
            gpuFloor = 96.9,
            rasterTolerance = 1,
        )
    }
}
