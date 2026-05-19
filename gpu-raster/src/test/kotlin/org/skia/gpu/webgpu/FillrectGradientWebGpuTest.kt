package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.FillrectGradientGM

/**
 * G4.x cross-test : `FillrectGradientGM` -- 120 x 540 canvas, 2-column
 * x 9-row grid of 50 x 50 rect cells. Each row is the same stop list
 * rendered once with `SkLinearGradient` (column 0) and once with
 * `SkRadialGradient` (column 1). Stops cover all the corner cases the
 * gradient infrastructure must handle : 2/3-stop endpoints, sub-range,
 * single-stop, disjoint via duplicate position, ignored duplicates,
 * unsorted input. All under kClamp tile mode on rect (in scope).
 */
class FillrectGradientWebGpuTest {

    @Test
    fun `FillrectGradientGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(
            FillrectGradientGM(),
            floor = 95.70,
            logTag = "FillrectGradientWebGpu",
        )
    }
}
