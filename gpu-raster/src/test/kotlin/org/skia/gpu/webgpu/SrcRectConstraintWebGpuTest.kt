package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SrcRectConstraintGM

@Disabled("STUB.SRC_RECT_CONSTRAINT: SkCanvas.drawImageRect lacks SrcRectConstraint param")
class SrcRectConstraintWebGpuTest {
    @Test
    fun `SrcRectConstraintGM placeholder`() {
        runGpuCrossTest(SrcRectConstraintGM(), floor = 0.0)
    }
}
