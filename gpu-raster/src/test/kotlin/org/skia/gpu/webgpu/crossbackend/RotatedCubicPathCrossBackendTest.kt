package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RotatedCubicPathGM

/**
 * Cross-backend test : `RotatedCubicPathGM` on raster + GPU.
 *
 * Two stacked cubic-only closed paths -- one drawn axis-aligned in
 * blue, one drawn rotated 90 deg in red. Tests AA-on-rotated-cubic-
 * fill : `c.rotate(90f)` between the two `drawPath` calls. The
 * rotated path tests cubic-flattening interaction with non-axis-
 * aligned CTM.
 *
 * Floors :
 *  - raster (tol=1) : 99.35 %
 *  - GPU (tol=8) : 99.57 %
 */
class RotatedCubicPathCrossBackendTest {

    @Test
    fun `RotatedCubicPathGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RotatedCubicPathGM(),
            rasterFloor = 99.35,
            gpuFloor = 99.57,
            rasterTolerance = 1,
        )
    }
}
