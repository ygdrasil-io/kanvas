package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug1139750GM

/**
 * Cross-backend test : `Crbug1139750GM` on raster + GPU.
 *
 * 50 x 50, `SK_ColorWHITE` bg, single stroked RRect. CTM =
 * `translate(10, 10) * scale(1.47619, 1.52381)` (non-square scale to
 * force the elliptical-RRect op). RRect = `MakeRectXY(rect(1, 1, 19,
 * 19), 1, 1)`, stroke width 2 (= 2 * radius -> inner radii collapse to
 * exactly zero). On the GPU backend pre-fix the shader used
 * `1 / innerRadius` to compute the coverage ramp, which became
 * infinity and the geometry disappeared. Regression repro of
 * crbug.com/1139750.
 *
 * Exercises the AA-stroke elliptical-RRect op on a degenerate-inner
 * (`r = 0`) configuration under non-square scale.
 *
 * Floors : raster 99.39 %, GPU 99.43 % (post-ratchet ~0.05 % below
 * measured).
 */
class Crbug1139750CrossBackendTest {

    @Test
    fun `Crbug1139750GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug1139750GM(),
            rasterFloor = 99.39,
            gpuFloor = 99.43,
        )
    }
}
