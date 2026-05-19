package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ScaledRectsGM

/**
 * Cross-backend test : `ScaledRectsGM` on raster + GPU.
 *
 * Stack of `drawPaint` (non-trivial bg colour) + `clipRect`
 * (axis-aligned) + `setMatrix` (rotated/skewed) + 2 large rects drawn
 * under that CTM (which routes through `drawPath` → 4-vertex polygon
 * fill) + `kPlus` blend mode. Exercises G3.3a polygon path + G3.3a.1
 * kPlus + G3.2 drawPaint bg + G6.0 colorspace transform on the GPU
 * side, and AA polygon coverage on the raster side.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ScaledRectsTest`, tol=1) : 85.0 %
 *  - GPU (`ScaledRectsWebGpuTest`, tol=8) : 99.99 %
 */
class ScaledRectsCrossBackendTest {

    @Test
    fun `ScaledRectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ScaledRectsGM(),
            rasterFloor = 85.0,
            gpuFloor = 99.99,
            rasterTolerance = 1,
        )
    }
}
