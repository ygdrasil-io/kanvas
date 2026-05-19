package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ConvexLineOnlyPathsFillGM

/**
 * Cross-test : `ConvexLineOnlyPathsFillGM` (`convex-lineonly-paths`)
 * on the GPU backend.
 *
 * 512 x 512, 20 convex line-only polygons (narrow rects, trapezoids,
 * teardrops, n-gons up to 100 sides) each drawn 7 times at scales
 * `{1, 0.75, 0.5, 0.25, 0.1, 0.01, 0.001}` alternating CW / CCW, AA on
 * with alternating black / white fill. Plus three crbug repros at the
 * end. The fill variant uses default `kFill_Style` so every path
 * exercises the AA polygon shader directly.
 */
class ConvexLineOnlyPathsFillWebGpuTest {

    @Test
    fun `ConvexLineOnlyPathsFillGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(
            ConvexLineOnlyPathsFillGM(),
            floor = 98.70,
            logTag = "ConvexLineOnlyPathsFillWebGpu",
        )
    }
}
