package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConicPathsGM

/**
 * Cross-backend test : `ConicPathsGM` on raster + GPU.
 *
 * Ten conic-Bezier paths (circle, hyperbolas, near-parabola, ellipses,
 * degenerate variants) drawn under every combination of alpha 0xFF /
 * 0x40, AA off / on, fill / stroke -- 8 cells per path x 10 paths = 80
 * cells, plus a "giant circle" at extreme coordinates that the
 * rasterizer must clip cleanly.
 *
 * Stresses `SkPathBuilder.conicTo` / `rConicTo` flattening with the
 * full range of conic weights (0.5, 0.999, 2, sqrt(2)/2). GPU score
 * (99.39 %) is substantially higher than raster (95.54 %) -- the
 * cross-test will catch any drift that closes that gap or widens it.
 *
 * Floors :
 *  - raster (tol=1) : 95.49 %
 *  - GPU (tol=8) : 99.34 %
 */
class ConicPathsCrossBackendTest {

    @Test
    fun `ConicPathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ConicPathsGM(),
            rasterFloor = 95.49,
            gpuFloor = 99.34,
            rasterTolerance = 1,
        )
    }
}
