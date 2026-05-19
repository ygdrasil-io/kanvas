package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ThinRectsGM

/**
 * Cross-backend test : `ThinRectsGM` on raster + GPU.
 *
 * Pure fill-style rects (WHITE / GREEN, opaque), kSrcOver only, AA on,
 * fractional axis-aligned positions, no clip, no stroke. The first GM
 * the WebGPU port hit 100 % on (post-G6.0 colorspace transform) ;
 * raster has been at 92 %+ since the AA polygon work. Useful as a
 * smoke test that the cross-backend harness handles "raster lags
 * behind GPU" cases cleanly (the inverse of every other GM).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ThinRectsTest`, tol=1) : 92.0 %
 *  - GPU (`ThinRectsWebGpuTest`, tol=8) : 99.99 %
 */
class ThinRectsCrossBackendTest {

    @Test
    fun `ThinRectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ThinRectsGM(),
            rasterFloor = 92.0,
            gpuFloor = 99.99,
            rasterTolerance = 1,
        )
    }
}
