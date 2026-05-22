package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.HairlineSubdivGM

/**
 * Cross-backend test : `HairlineSubdivGM` (`hairline_subdiv`) on raster
 * + GPU.
 *
 * 512 x 256 canvas, 4 increasingly-large AA hairline (strokeWidth = 1)
 * quadratic Bezier strokes, overlaid at progressively-shifted origins.
 * Each draw exercises a different subdivision count in the Bezier
 * flattener routed through G3.4.3 hairline synthesis + G3.4.1 SkStroker.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round5Test`, tol=1) : 95.0 % ;
 *  - GPU (`HairlineSubdivWebGpuTest`, tol=8) : 97.45 %.
 */
class HairlineSubdivCrossBackendTest {

    @Test
    fun `HairlineSubdivGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = HairlineSubdivGM(),
            rasterFloor = 95.0,
            gpuFloor = 97.45,
            rasterTolerance = 1,
        )
    }
}
