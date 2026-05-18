package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ArcCircleGapGM

/**
 * Cross-test : `ArcCircleGapGM` on the GPU backend.
 *
 * Stroked circle + stroked tangent-arc, both at huge radius (~1097),
 * exercising the sub-pixel-gap regression case from upstream Skia.
 * Uses default hairline stroke (`strokeWidth = 0`), so routes through
 * the G3.4.3 `1 / resScale` synthesis path before SkStroker.
 */
class ArcCircleGapWebGpuTest {

    @Test
    fun `ArcCircleGapGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ArcCircleGapGM(), floor = 99.05)
    }
}
