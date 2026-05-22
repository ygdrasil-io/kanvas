package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnalyticAntialiasConvexGM

/**
 * Cross-backend test : `AnalyticAntialiasConvexGM` on raster + GPU.
 *
 * Single AA-filled convex polygon path. Exercises the analytic AA path
 * for the convex single-contour case -- one polygon routed through the
 * convex-AA fast-path with per-edge analytic coverage. Edge-pixel drift
 * dominates the floor.
 *
 * Floors : GPU 99.85 % / raster 99.73 % (initial run 99.90 % / 99.78 %).
 */
class AnalyticAntialiasConvexCrossBackendTest {

    @Test
    fun `AnalyticAntialiasConvexGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = AnalyticAntialiasConvexGM(),
            rasterFloor = 99.73,
            gpuFloor = 99.85,
        )
    }
}
