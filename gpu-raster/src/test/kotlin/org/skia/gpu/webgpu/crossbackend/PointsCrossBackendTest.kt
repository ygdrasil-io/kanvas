package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PointsGM

/**
 * Cross-backend test : `PointsGM` on raster + GPU.
 *
 * 640 x 490 canvas, 99 deterministic-`SkRandom` points drawn in four
 * passes : kPolygon (red 4-px stroke), kLines (green hairline), kPoints
 * (blue 6-px round-cap), kPoints (white hairline butt-cap on top). The
 * white butt-cap hairline points exercise the degenerate-vertex
 * filter (zero-extent stroker output for a single point with kButt
 * cap) -- previously panicked, now silently skipped post-#567.
 *
 * Floors :
 *  - raster (tol=1) : 99.44 %
 *  - GPU (tol=8) : 99.45 %
 */
class PointsCrossBackendTest {

    @Test
    fun `PointsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PointsGM(),
            rasterFloor = 99.39,
            gpuFloor = 99.40,
            rasterTolerance = 1,
        )
    }
}
