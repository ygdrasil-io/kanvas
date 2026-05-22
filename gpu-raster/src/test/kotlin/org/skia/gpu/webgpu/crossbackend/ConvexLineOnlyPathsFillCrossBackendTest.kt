package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConvexLineOnlyPathsFillGM

/**
 * Cross-backend test : `ConvexLineOnlyPathsFillGM`
 * (`convex-lineonly-paths`) on raster + GPU.
 *
 * 512 x 512, 20 convex line-only polygons (narrow rects, trapezoids,
 * teardrops, n-gons up to 100 sides) each drawn 7 times at scales
 * `{1, 0.75, 0.5, 0.25, 0.1, 0.01, 0.001}` alternating CW / CCW, AA on
 * with alternating black / white fill. Plus three crbug repros at the
 * end. Default `kFill_Style` so every path exercises the AA polygon
 * shader directly (G3.3b on convex line-only input).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round6Test`, tol=1) : 94.0 % ;
 *  - GPU (`ConvexLineOnlyPathsFillWebGpuTest`, tol=8) : 98.70 %.
 */
class ConvexLineOnlyPathsFillCrossBackendTest {

    @Test
    fun `ConvexLineOnlyPathsFillGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ConvexLineOnlyPathsFillGM(),
            rasterFloor = 94.0,
            gpuFloor = 98.70,
            rasterTolerance = 1,
        )
    }
}
