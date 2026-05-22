package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AnalyticAntialiasInverseGM

/**
 * Cross-backend test : `AnalyticAntialiasInverseGM` on raster + GPU.
 *
 * Inverse-fill variant of `AnalyticAntialiasConvexGM`. Single AA-filled
 * convex polygon path drawn with `kInverseWinding` fill type. Exercises
 * the analytic AA path -- a single convex contour routed through the
 * polygon AA dispatcher with the fill-everywhere-but-this-shape inversion
 * applied on the cover step.
 *
 * Floors : GPU 99.93 % / raster 99.92 % (initial run 99.98 % / 99.97 %).
 */
class AnalyticAntialiasInverseCrossBackendTest {

    @Test
    fun `AnalyticAntialiasInverseGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = AnalyticAntialiasInverseGM(),
            rasterFloor = 99.92,
            gpuFloor = 99.93,
        )
    }
}
